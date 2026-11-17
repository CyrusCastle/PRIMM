file = open("poem.txt", "r")
text = file.read()
file.close()
lines = text.splitlines()
words = text.split()
print("Lines:", len(lines))
print("Words:", len(words))
longest = ""
for word in words:
    if len(word) > len(longest):
        longest = word
print("Longest word:", longest)
