"""
Warning, this file ("runner.py"), along with "bridge.js" and "python-worker.js" were AI generated.
Do not expect craftsmanship or honour ahead, remember that the machine lies.

Executes a student's program inside Pyodide.

The worker loads this file once, then calls ``primm_run`` for every run.
All communication with the page goes through four callables passed in
from JavaScript, so this file has no browser-specific code and can be
tested under Node.

    write_out(text)       -> None        text printed to stdout
    write_err(text)       -> None        text printed to stderr
    read_input(prompt)    -> str | None  blocks until the student types a
                                         line; None means "stop the program"
    sleep(seconds)        -> bool        blocks; True means "stop the program"

and, when tracing (the app always traces; the tests often don't):

    trace_start(index, line, frame, scope)   a line is about to run
    trace_done(index, changes_json, output)  that line has finished
    step_wait()           -> str | None  blocks before each line while stepping:
                                         "next", "continue", or None to stop

``primm_run`` returns a JSON string describing how the run ended.
``primm_analyse`` returns a JSON description of a program's structure.

Programs can also ``from weather import temperature``: live temperatures from
Open-Meteo, fetched with the browser's XMLHttpRequest (the tests supply a fake).
"""

import ast
import builtins
import io
import json
import linecache
import os
import shutil
import sys
import time
import traceback
import types
import urllib.parse

try:  # JavaScript null arrives as pyodide.ffi.jsnull rather than None.
    from pyodide.ffi import jsnull as _JS_NULL
except ImportError:  # pragma: no cover - only outside Pyodide
    _JS_NULL = None

PROGRAM_FILENAME = "main.py"
MAX_OUTPUT_LINES = 5000
MAX_TRACE_ROWS = 500
MAX_VALUE_LENGTH = 40

# The Files tab: each run gets a fresh folder holding just the app's files.
FILES_FOLDER = "/home/pyodide/primm-files"
MAX_FILES = 20
MAX_FILE_CHARS = 100_000


class OutputLimitReached(BaseException):
    """Raised when a program prints more than MAX_OUTPUT_LINES lines.

    It derives from BaseException so that a student's ``except Exception``
    cannot swallow it.
    """


class _Stream:
    """A minimal text stream that forwards everything it is given."""

    def __init__(self, forward, counter, tracer=None):
        self._forward = forward
        self._counter = counter
        self._tracer = tracer

    def write(self, text):
        text = str(text)
        if text:
            self._counter.add(text)
            if self._tracer is not None:
                self._tracer.capture(text)
            self._forward(text)
        return len(text)

    def writelines(self, lines):
        for line in lines:
            self.write(line)

    def flush(self):
        pass

    def isatty(self):
        return False

    def readable(self):
        return False

    def writable(self):
        return True

    @property
    def encoding(self):
        return "utf-8"


class _LineCounter:
    def __init__(self, limit):
        self.limit = limit
        self.lines = 0

    def add(self, text):
        self.lines += text.count("\n")
        if self.lines > self.limit:
            raise OutputLimitReached()


# ---------------------------------------------------------------- tracing


def _short_repr(value):
    if isinstance(value, io.IOBase):
        # Python's own repr is long and means little to a student.
        name = getattr(value, "name", "?")
        mode = getattr(value, "mode", "")
        return f"<file {name!r} {mode}{', closed' if value.closed else ''}>"[:MAX_VALUE_LENGTH]
    try:
        text = repr(value)
    except Exception:  # noqa: BLE001 - a broken __repr__ must not stop the trace
        text = "<?>"
    if len(text) > MAX_VALUE_LENGTH:
        text = text[: MAX_VALUE_LENGTH - 1] + "\u2026"
    return text


_HIDDEN_TYPES = (
    types.ModuleType,
    types.FunctionType,
    types.BuiltinFunctionType,
    types.MethodType,
    type,
)


class _Tracer:
    """Records each line the student's program runs, using sys.settrace.

    A row starts when a line is about to run and finishes when the next line
    in the same frame starts (or the frame returns). Its changes are the
    variables whose value (as repr) differs between those two moments, so a
    line that calls a function gets the assignment after the call returns.
    Function variables are named "function.variable".
    """

    def __init__(self, on_start, on_done, step_wait, stepping):
        self._on_start = on_start
        self._on_done = on_done
        self._step_wait = step_wait
        self.stepping = stepping
        self.active = True
        self.truncated = False
        self.rows = 0
        self._pending = {}  # frame -> [row index, snapshot before the line]
        self._frame_ids = {}
        self._next_frame_id = 1
        self._outputs = {}
        self._current = None  # the row that receives printed output

    def global_trace(self, frame, event, arg):
        if not self.active or frame.f_code.co_filename != PROGRAM_FILENAME:
            return None
        self._frame_ids[frame] = self._next_frame_id
        self._next_frame_id += 1
        return self._local_trace

    def _local_trace(self, frame, event, arg):
        if not self.active:
            return None
        if event == "line":
            self._finish(frame)
            if self.rows >= MAX_TRACE_ROWS:
                self._stop_recording()
                return None
            self._start(frame)
            if self.stepping:
                answer = self._step_wait()
                if answer is None or answer is _JS_NULL:
                    raise KeyboardInterrupt
                if str(answer) == "continue":
                    self.stepping = False
        elif event == "return":
            self._finish(frame)
            caller = self._pending.get(frame.f_back)
            self._current = caller[0] if caller else None
            self._frame_ids.pop(frame, None)
        return self._local_trace

    def capture(self, text):
        if self.active and self._current is not None:
            self._outputs.setdefault(self._current, []).append(text)

    def finish_all(self):
        for frame in list(self._pending):
            self._finish(frame)

    def _start(self, frame):
        index = self.rows
        self.rows += 1
        self._pending[frame] = [index, self._snapshot(frame)]
        self._current = index
        scope = "" if frame.f_code.co_name == "<module>" else frame.f_code.co_name
        self._on_start(index, frame.f_lineno, self._frame_ids.get(frame, 0), scope)

    def _finish(self, frame):
        entry = self._pending.pop(frame, None)
        if entry is None:
            return
        index, before = entry
        after = self._snapshot(frame)
        changes = {name: value for name, value in after.items() if before.get(name) != value}
        output = "".join(self._outputs.pop(index, []))
        self._on_done(index, json.dumps(changes), output)

    def _stop_recording(self):
        """Too many rows: stop tracing but let the program carry on."""
        self.finish_all()
        self.active = False
        self.truncated = True
        self.stepping = False
        sys.settrace(None)

    @staticmethod
    def _snapshot(frame):
        if frame.f_code.co_name == "<module>":
            namespace, prefix = frame.f_globals, ""
        else:
            namespace, prefix = frame.f_locals, frame.f_code.co_name + "."
        values = {}
        for name, value in list(namespace.items()):
            if name.startswith("__") or isinstance(value, _HIDDEN_TYPES):
                continue
            values[prefix + name] = _short_repr(value)
        return values


# ---------------------------------------------------------------- weather
# `from weather import temperature` gives programs the temperature now at any
# named place, from Open-Meteo (open-meteo.com: free, no key). The request is
# synchronous, which browsers allow in a worker. Places and readings are kept
# for a while so that a class running a program again and again stays well
# inside the service's daily limit, which a school shares through one address.

WEATHER_PLACE_URL = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
WEATHER_READING_URL = "https://api.open-meteo.com/v1/forecast?current=temperature_2m"
WEATHER_KEEP_SECONDS = 600
WEATHER_TIMEOUT_MS = 10_000

_weather_places = {}    # lower-case name -> (latitude, longitude)
_weather_readings = {}  # lower-case name -> (time read, temperature)


def _weather_get(url):
    from js import XMLHttpRequest

    request = XMLHttpRequest.new()
    try:
        request.open("GET", url, False)
        request.timeout = WEATHER_TIMEOUT_MS
        request.send(None)
    except Exception:
        raise ConnectionError("could not reach the weather service") from None
    if request.status != 200:
        raise ConnectionError(f"the weather service answered {request.status}")
    return json.loads(str(request.responseText))


def _weather_place(name):
    key = name.lower()
    if key not in _weather_places:
        found = _weather_get(WEATHER_PLACE_URL + urllib.parse.quote(name)).get("results") or []
        if not found:
            raise ValueError(f"unknown place: {name!r}")
        _weather_places[key] = (found[0]["latitude"], found[0]["longitude"])
    return _weather_places[key]


def _weather_temperature(place):
    """The temperature now at a place, in degrees Celsius (e.g. 9.4)."""
    name = " ".join(str(place).split())
    if not name:
        raise ValueError("unknown place: ''")
    key = name.lower()
    kept = _weather_readings.get(key)
    if kept is not None and time.time() - kept[0] < WEATHER_KEEP_SECONDS:
        return kept[1]
    latitude, longitude = _weather_place(name)
    data = _weather_get(f"{WEATHER_READING_URL}&latitude={latitude}&longitude={longitude}")
    reading = float(data["current"]["temperature_2m"])
    _weather_readings[key] = (time.time(), reading)
    return reading


def _install_weather_module():
    module = types.ModuleType("weather", "Live temperatures from Open-Meteo (open-meteo.com).")
    _weather_temperature.__name__ = _weather_temperature.__qualname__ = "temperature"
    _weather_temperature.__module__ = "weather"
    module.temperature = _weather_temperature
    sys.modules["weather"] = module


_install_weather_module()


# --------------------------------------------------------------- analysis


def _unique(items):
    seen = set()
    result = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def primm_analyse(code):
    """Describes a program for the Predict row, as JSON.

    Returns {"ok": false} if the program doesn't parse. Otherwise lists:
    variables (in order of first assignment), input() calls (and whether each
    is inside a loop), if/elif/while lines with the first line of their body,
    loops with their body range and the names in their condition or target,
    and print() lines. Loops and conditions name their function in "scope"
    ("" at the top level). "lists" says whether the program uses lists, and
    "files" lists the open() calls with the file name and mode when they are
    written out (name null when it isn't a literal, mode "?" likewise).

    For trace tables it also gives: "fileVariables" (names that hold an open
    file), "overFile" on for loops that read a file line by line, "assigned"
    (every line each variable is set or changed on, counting items.append(x)
    and items[0] = x), "literals" (top-level `name = value`
    lines whose value is written out, such as `top = -1`) and "chains" (each
    if with its elif lines and whether it ends in else).
    """
    try:
        tree = ast.parse(code, PROGRAM_FILENAME)
    except (SyntaxError, ValueError):
        return json.dumps({"ok": False})

    source_lines = code.splitlines()
    variables = []  # (line, column, name)
    inputs, conditions, loops, prints, functions, files = [], [], [], [], [], []
    file_variables, chains = [], []
    changed_in_place = []  # (line, column, name) for items.append(x), items[0] = x

    def qualified(scope, name):
        return f"{scope}.{name}" if scope else name

    def walk(node, scope, in_loop):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            functions.append(node.name)
            arguments = node.args.posonlyargs + node.args.args + node.args.kwonlyargs
            for argument in arguments:
                variables.append((node.lineno, argument.col_offset, qualified(node.name, argument.arg)))
            for child in node.body:
                walk(child, node.name, False)
            return
        if isinstance(node, (ast.ClassDef, ast.Lambda)):
            return
        if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Store):
            variables.append((node.lineno, node.col_offset, qualified(scope, node.id)))
        if isinstance(node, ast.If):
            kind = "elif" if _is_elif(node, source_lines) else "if"
            conditions.append({"line": node.lineno, "kind": kind, "bodyLine": node.body[0].lineno, "scope": scope})
            if kind == "if":
                branches, last = [node.lineno], node
                while len(last.orelse) == 1 and isinstance(last.orelse[0], ast.If) and _is_elif(last.orelse[0], source_lines):
                    last = last.orelse[0]
                    branches.append(last.lineno)
                chains.append({"head": node.lineno, "branches": branches, "hasElse": bool(last.orelse), "scope": scope})
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr in _CHANGING_METHODS \
                and isinstance(node.func.value, ast.Name):
            changed_in_place.append((node.lineno, node.col_offset, qualified(scope, node.func.value.id)))
        if isinstance(node, ast.Subscript) and isinstance(node.ctx, (ast.Store, ast.Del)) and isinstance(node.value, ast.Name):
            changed_in_place.append((node.lineno, node.col_offset, qualified(scope, node.value.id)))
        if isinstance(node, ast.Assign) and _is_open_call(node.value):
            for target in node.targets:
                if isinstance(target, ast.Name):
                    file_variables.append(qualified(scope, target.id))
        if isinstance(node, (ast.With, ast.AsyncWith)):
            for item in node.items:
                if _is_open_call(item.context_expr) and isinstance(item.optional_vars, ast.Name):
                    file_variables.append(qualified(scope, item.optional_vars.id))
        if isinstance(node, ast.While):
            conditions.append({"line": node.lineno, "kind": "while", "bodyLine": node.body[0].lineno, "scope": scope})
            names = _unique(n.id for n in ast.walk(node.test) if isinstance(n, ast.Name))
            loops.append({
                "line": node.lineno, "kind": "while", "bodyLine": node.body[0].lineno,
                "endLine": node.end_lineno, "names": [qualified(scope, n) for n in names], "scope": scope,
            })
        if isinstance(node, ast.For):
            names = _unique(n.id for n in ast.walk(node.target) if isinstance(n, ast.Name))
            loops.append({
                "line": node.lineno, "kind": "for", "bodyLine": node.body[0].lineno,
                "endLine": node.end_lineno, "names": [qualified(scope, n) for n in names], "scope": scope,
                "_iter": node.iter,
            })
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            if node.func.id == "input":
                prompt = ast.get_source_segment(code, node.args[0]) if node.args else ""
                inputs.append({"line": node.lineno, "prompt": prompt or "", "inLoop": in_loop})
            elif node.func.id == "print":
                prints.append(node.lineno)
            elif node.func.id == "open":
                files.append({"line": node.lineno, "name": _constant_text(node.args[0] if node.args else None),
                              "mode": _open_mode(node)})
        is_loop = isinstance(node, (ast.For, ast.While))
        for child in ast.iter_child_nodes(node):
            # A for loop's iterable is worked out once; everything else in a loop repeats.
            repeats = is_loop and not (isinstance(node, ast.For) and child is node.iter)
            walk(child, scope, in_loop or repeats)

    for statement in tree.body:
        walk(statement, "", False)

    file_variables = _unique(file_variables)
    for loop in loops:
        source = loop.pop("_iter", None)
        if loop["kind"] == "for":
            reads_name = isinstance(source, ast.Name) and qualified(loop["scope"], source.id) in file_variables
            if reads_name or _is_open_call(source):
                loop["overFile"] = True
    assigned = {}
    for line, _, name in sorted(variables, key=lambda v: (v[0], v[1])):
        if line not in assigned.setdefault(name, []):
            assigned[name].append(line)
    for line, _, name in sorted(changed_in_place):
        if name in assigned and line not in assigned[name]:
            assigned[name] = sorted(assigned[name] + [line])
    literals = []
    for statement in tree.body:
        if isinstance(statement, ast.Assign) and len(statement.targets) == 1 and isinstance(statement.targets[0], ast.Name):
            text = _literal_text(statement.value, code)
            if text is not None:
                literals.append({"line": statement.lineno, "name": statement.targets[0].id, "text": text})

    ordered = [name for _, _, name in sorted(variables, key=lambda v: (v[0], v[1]))]
    return json.dumps({
        "lists": _uses_lists(tree),
        "ok": True,
        "variables": _unique(ordered),
        "inputs": inputs,
        "conditions": sorted(conditions, key=lambda c: c["line"]),
        "loops": sorted(loops, key=lambda l: l["line"]),
        "prints": sorted(set(prints)),
        "functions": functions,
        "files": sorted(files, key=lambda f: f["line"]),
        "lineCount": len(source_lines),
        "fileVariables": file_variables,
        "assigned": assigned,
        "literals": literals,
        "chains": sorted(chains, key=lambda c: c["head"]),
    })


def _is_elif(node, source_lines):
    text = source_lines[node.lineno - 1].lstrip() if node.lineno <= len(source_lines) else ""
    return text.startswith("elif")


def _is_open_call(node):
    return isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "open"


def _literal_text(node, code):
    """The source of a value written out in full (5, -1, "", [], ["a", "b"]), or None."""
    def plain(n):
        if isinstance(n, ast.Constant):
            return True
        if isinstance(n, ast.UnaryOp) and isinstance(n.op, (ast.USub, ast.UAdd)):
            return isinstance(n.operand, ast.Constant) and isinstance(n.operand.value, (int, float))
        return False

    if plain(node) or (isinstance(node, (ast.List, ast.Tuple)) and all(plain(e) for e in node.elts)) or \
            (isinstance(node, ast.Dict) and not node.keys):
        return ast.get_source_segment(code, node)
    return None


def _constant_text(node):
    """The text of a string literal, or None for anything else."""
    return node.value if isinstance(node, ast.Constant) and isinstance(node.value, str) else None


def _open_mode(call):
    """open()'s mode as written: "r" when left out, "?" when worked out at run time."""
    node = call.args[1] if len(call.args) > 1 else next((k.value for k in call.keywords if k.arg == "mode"), None)
    if node is None:
        return "r"
    return _constant_text(node) or "?"


_LIST_METHODS = {"append", "insert", "pop", "remove", "sort", "extend", "reverse"}

# Methods that change a list, dictionary or set in place.
_CHANGING_METHODS = _LIST_METHODS | {"clear", "update", "setdefault", "popitem", "add", "discard"}


def _uses_lists(tree):
    """True if the program makes a list or calls a list method (for the Make concept checklist)."""
    for node in ast.walk(tree):
        if isinstance(node, (ast.List, ast.ListComp)):
            return True
        if isinstance(node, ast.Call):
            if isinstance(node.func, ast.Name) and node.func.id == "list":
                return True
            if isinstance(node.func, ast.Attribute) and node.func.attr in _LIST_METHODS:
                return True
    return False


def _user_frames_only(exc):
    """Format a traceback, keeping only frames from the student's program."""
    te = traceback.TracebackException.from_exception(exc)
    te.stack = traceback.StackSummary.from_list(
        [frame for frame in te.stack if frame.filename == PROGRAM_FILENAME]
    )
    return "".join(te.format())


def _error_line(exc):
    if isinstance(exc, SyntaxError) and exc.filename == PROGRAM_FILENAME:
        return exc.lineno
    line = None
    tb = exc.__traceback__
    while tb is not None:
        if tb.tb_frame.f_code.co_filename == PROGRAM_FILENAME:
            line = tb.tb_lineno
        tb = tb.tb_next
    return line


def _result(outcome, **extra):
    data = {"outcome": outcome}
    data.update(extra)
    return json.dumps(data)


def primm_run(code, write_out, write_err, read_input, sleep,
              trace_start=None, trace_done=None, step_wait=None, stepping=False, files_json=None):
    """Runs the student's program and returns its outcome as JSON.

    With files_json (a JSON list of {"name", "text"}), the program runs in a
    fresh folder holding just those files, and the outcome lists the files as
    they are afterwards under "files" (each cut to MAX_FILE_CHARS, marked
    "truncated").
    """
    if files_json is None or files_json is _JS_NULL:
        return _run(code, write_out, write_err, read_input, sleep, trace_start, trace_done, step_wait, stepping)
    previous = os.getcwd()
    try:
        _prepare_files(json.loads(str(files_json)))
        os.chdir(FILES_FOLDER)
        result = json.loads(
            _run(code, write_out, write_err, read_input, sleep, trace_start, trace_done, step_wait, stepping)
        )
        result["files"] = _collect_files()
        return json.dumps(result)
    finally:
        os.chdir(previous)


def _safe_file_name(name):
    name = str(name)
    return name and name == os.path.basename(name) and name not in (".", "..")


def _prepare_files(files):
    shutil.rmtree(FILES_FOLDER, ignore_errors=True)
    os.makedirs(FILES_FOLDER)
    for entry in files[:MAX_FILES]:
        name = entry.get("name", "")
        if not _safe_file_name(name):
            continue
        with open(os.path.join(FILES_FOLDER, name), "w", encoding="utf-8", newline="") as file:
            file.write(str(entry.get("text", "")))


def _collect_files():
    collected = []
    for name in sorted(os.listdir(FILES_FOLDER)):
        path = os.path.join(FILES_FOLDER, name)
        if not os.path.isfile(path) or len(collected) >= MAX_FILES:
            continue
        with open(path, "r", encoding="utf-8", errors="replace", newline="") as file:
            text = file.read(MAX_FILE_CHARS + 1)
        entry = {"name": name, "text": text[:MAX_FILE_CHARS]}
        if len(text) > MAX_FILE_CHARS:
            entry["truncated"] = True
        collected.append(entry)
    return collected


def _run(code, write_out, write_err, read_input, sleep, trace_start, trace_done, step_wait, stepping):
    counter = _LineCounter(MAX_OUTPUT_LINES)
    tracer = None
    if trace_start is not None and trace_done is not None:
        tracer = _Tracer(trace_start, trace_done, step_wait, bool(stepping) and step_wait is not None)
    out = _Stream(write_out, counter, tracer)
    err = _Stream(write_err, counter, tracer)

    def student_input(prompt=""):
        prompt = str(prompt)
        if prompt:
            out.write(prompt)
        caller = sys._getframe(1)
        call_line = caller.f_lineno if caller.f_code.co_filename == PROGRAM_FILENAME else None
        line = read_input(prompt, call_line)
        if line is None or line is _JS_NULL:
            raise KeyboardInterrupt
        line = str(line)
        # Treat a pasted multi-line answer as its first line, like a terminal.
        return line.split("\n", 1)[0].rstrip("\r")

    def student_sleep(seconds):
        seconds = float(seconds)
        if seconds < 0:
            raise ValueError("sleep length must be non-negative")
        if sleep(seconds):
            raise KeyboardInterrupt

    # Make the program's source available to tracebacks.
    linecache.cache[PROGRAM_FILENAME] = (
        len(code),
        None,
        code.splitlines(True),
        PROGRAM_FILENAME,
    )

    saved = (sys.stdout, sys.stderr, builtins.input, time.sleep)
    sys.stdout, sys.stderr = out, err
    builtins.input = student_input
    time.sleep = student_sleep
    program_globals = {"__name__": "__main__", "__builtins__": builtins}

    def finished(outcome, **extra):
        if tracer is not None:
            extra["traceTruncated"] = tracer.truncated
        return _result(outcome, **extra)

    try:
        compiled = compile(code, PROGRAM_FILENAME, "exec")
        if tracer is not None:
            sys.settrace(tracer.global_trace)
        try:
            exec(compiled, program_globals)
        finally:
            sys.settrace(None)
            if tracer is not None:
                tracer.finish_all()
        return finished("completed")
    except KeyboardInterrupt:
        return finished("stopped")
    except OutputLimitReached:
        return finished(
            "error",
            type="OutputLimit",
            line=None,
            message=f"The program printed more than {MAX_OUTPUT_LINES} lines, "
            "so it was stopped. Is there an infinite loop?",
            traceback="",
        )
    except SystemExit as exc:
        if exc.code in (None, 0):
            return finished("completed")
        return finished(
            "error",
            type="SystemExit",
            line=_error_line(exc),
            message=str(exc.code),
            traceback="",
        )
    except BaseException as exc:  # noqa: BLE001 - report every student error
        # str() of a SyntaxError appends "(main.py, line N)"; .msg is the message alone.
        message = exc.msg if isinstance(exc, SyntaxError) and exc.msg else str(exc)
        # File errors read "[Errno 44] No such file or directory: 'x'"; the number means nothing here.
        if isinstance(exc, OSError) and exc.strerror:
            message = exc.strerror + (f": {exc.filename!r}" if exc.filename is not None else "")
        return finished(
            "error",
            type=type(exc).__name__,
            line=_error_line(exc),
            message=message,
            traceback=_user_frames_only(exc),
        )
    finally:
        sys.stdout, sys.stderr, builtins.input, time.sleep = saved
