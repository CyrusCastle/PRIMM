score = int(input("Score out of 100: "))
if score >= 70:
    grade = "A"
elif score >= 50:
    grade = "B"
elif score >= 30:
    grade = "C"
else:
    grade = "U"
print("Your grade is", grade)
