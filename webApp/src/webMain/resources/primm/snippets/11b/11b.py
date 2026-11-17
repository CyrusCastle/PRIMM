top_name = ""
top_score = -1
total = 0
count = 0
results = open("results.csv", "r")

for line in results:
    name, score = line.strip().split(",")
    score = int(score)
    total = total + score
    count = count + 1
    if score > top_score:
        top_score = score
        top_name = name

results.close()

print("Class average:", round(total / count, 1))
summary = open("summary.txt", "w")
summary.write("Top scorer: " + top_name + " with " + str(top_score) + "\n")
summary.close()
