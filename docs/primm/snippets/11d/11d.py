from datetime import date
from weather import temperature # Weather data by Open-Meteo.com

places = ["Cardiff", "London", "Edinburgh"]
total = 0

for place in places:
    temp = temperature(place)
    print(place + ":", temp, "°C")
    total = total + temp

average = round(total / len(places), 1)

today = date.today().strftime("%d/%m/%Y")

log = open("temperatures.txt", "a")
log.write(today + " - avg of " + str(average) + " celsius\n")
log.close()

print("Saved:", today, "- avg of", average, "celsius")
