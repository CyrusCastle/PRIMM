from datetime import date
from weather import temperature # Weather data by Open-Meteo.com

places = ["Cardiff", "London", "Edinburgh"]
temps = [temperature(place) for place in places]

total = sum(temps)
average = round(total / len(temps), 1)

today = date.today().strftime("%d/%m/%Y")

log = open("temperatures.txt", "a")
log.write(today + " - avg of " + str(average) + " celsius\n")
log.close()

print("Saved:", today, "- avg of", average, "celsius")
