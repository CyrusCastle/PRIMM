print("What's your name?")
name = input()
print("Hi", name + "! Type bye to leave.")

message = ""
while message != "bye":
    message = input("").lower()
    if message == "bye":
        print("Goodbye,", name)
    elif "hello" in message:
        print("Hello to you too!")
    elif "?" in message:
        print("Good question. I don't know!")
    else:
        print("Tell me more...")
