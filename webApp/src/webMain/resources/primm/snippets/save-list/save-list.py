colours = ["red", "green", "blue"]
file = open("colours.txt", "w")
for colour in colours:
    file.write(colour + "\n")
file.close()
print("Saved", len(colours), "colours")
