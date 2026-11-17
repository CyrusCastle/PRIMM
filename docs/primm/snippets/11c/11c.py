pass_mark = 10
saved = 0
scores = open("scores.txt", "r")
passed = open("passed.txt", "w")
for line in scores:
    score = int(line)
    if score >= pass_mark:
        passed.write(str(score) + "\n")
        saved = saved + 1
scores.close()
passed.close()
print(saved, "scores saved to passed.txt")
