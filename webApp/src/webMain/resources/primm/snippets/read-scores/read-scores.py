total = 0
count = 0
file = open("scores.txt", "r")
for line in file:
    name, score = line.strip().split(",")
    total = total + int(score)
    count = count + 1
    print(name, "scored", score)
file.close()
print("Average:", total / count)
