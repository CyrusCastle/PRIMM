from datetime import date
from functools import reduce
from operator import add
from weather import temperature # Weather data by Open-Meteo.com

places = ["Cardiff", "London", "Edinburgh"]
temps = list(map(temperature, places))

total = reduce(add, temps)
average = round(total / len(temps), 1)

today = date.today().strftime("%d/%m/%Y")

log = open("temperatures.txt", "a")
log.write(today + " - avg of " + str(average) + " celsius\n")
log.close()

print("Saved:", today, "- avg of", average, "celsius")
