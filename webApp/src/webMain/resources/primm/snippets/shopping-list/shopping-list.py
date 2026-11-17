items = []
for i in range(3):
    item = input("Add an item: ")
    items.append(item)
print("You need", len(items), "things:")
for item in items:
    print("-", item)
