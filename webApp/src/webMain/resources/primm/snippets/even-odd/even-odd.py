def is_even(number):
    return number % 2 == 0

for n in range(1, 6):
    if is_even(n):
        print(n, "is even")
    else:
        print(n, "is odd")
