// Serve the development build cross-origin isolated. This lets Python use
// SharedArrayBuffer, so input() waits efficiently and Stop can interrupt a
// running program without restarting Python. Without these headers the app
// still works, using the service-worker fallback in primm-sw.js.
//
// Your production server should send the same two headers (see README.md).
if (config.devServer) {
    config.devServer.headers = Object.assign({}, config.devServer.headers, {
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
    });
}
