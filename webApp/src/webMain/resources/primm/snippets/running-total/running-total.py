total = 0
for i in range(4):
    price = int(input("Price of item: "))
    total = total + price
    print("Total so far:", total)
print("You owe", total)
