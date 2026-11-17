highest = 0
file = open("scores.txt", "r")
for line in file:
    score = int(line)
    if score > highest:
        highest = score
file.close()
print("Highest score:", highest)
