from weather import temperature # Weather data by Open-Meteo.com

places = ["Hornsea", "Bridlington", "Scarborough"]
for place in places:
    temp = temperature(place)
    if temp < 10:
        message = "It's cold outside!"
    elif temp > 25:
        message = "It's boiling!"
    else:
        message = "Normal temperature today."
    print(place + ":", temp, "°C.", message)
