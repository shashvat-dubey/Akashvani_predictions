import pandas as pd
import mysql.connector
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestRegressor
from sqlalchemy import create_engine
from datetime import datetime

# Read and prepare historical weather data
weather = pd.read_csv(r"C:\Users\shshv\IdeaProjects\multi-task\python1\chennai 2022-02-01 to 2024-10-03.csv")
weather = weather.set_index("datetime")

# Modify target creation to use current temperature with next day's conditions
weather["next_humidity"] = weather["humidity"].shift(-1)
weather["next_windspeed"] = weather["windspeed"].shift(-1)
weather["target"] = weather["temp"].shift(-1)

# Drop rows with NaN values after shifting
weather = weather.dropna()

# Prepare features using current temperature with next day's conditions
X = pd.DataFrame({
    'temp': weather['temp'],
    'next_humidity': weather['next_humidity'],
    'next_windspeed': weather['next_windspeed']
})
y = weather["target"].values

# Train the model
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
model = RandomForestRegressor(n_estimators=100, random_state=42)
model.fit(X_train, y_train)

# Database connection settings
MYSQL_HOST = "localhost"
MYSQL_USER = "root"
MYSQL_PASSWORD = "Hellokitty69"
MYSQL_DATABASE = "weather_db"

# Create SQLAlchemy engine
engine = create_engine(f'mysql+mysqlconnector://{MYSQL_USER}:{MYSQL_PASSWORD}@{MYSQL_HOST}/{MYSQL_DATABASE}')

# Read current weather data
query = "SELECT * FROM weather_data"
df = pd.read_sql(query, engine)

# Prepare features for prediction
# Note: temperature in df is already today's temperature,
# and humidity/wind_speed are tomorrow's values
X_current = pd.DataFrame({
    'temp': df['temperature'],
    'next_humidity': df['humidity'],
    'next_windspeed': df['wind_speed']
})

# Make predictions
y_preds = model.predict(X_current)

# Prepare and save predictions
predictions_df = pd.DataFrame({
    'city': df['city'],
    'preds': y_preds
})

# Insert predictions into database
predictions_df.to_sql('predictions', con=engine, if_exists='append', index=False)
print("Predictions successfully generated and stored in database!")