package org.example;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class WeatherApp {
    private static final Logger LOGGER = Logger.getLogger(WeatherApp.class.getName());
    private static final String API_KEY = "E68W3EG8ST7LR84CX3K9JGB2M";
    private static final String MYSQL_HOST = "localhost";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "Hellokitty69";
    private static final String MYSQL_DATABASE = "weather_db";

    private WeatherGUI gui;
    private static final String[] SUPPORTED_CITIES = {"Chennai", "Bangalore", "Delhi"};
    private JSONObject[] cityWeatherData;
    private JSONObject[] tomorrowWeatherData;
    private Connection dbConnection;
    private String currentStoredCity = null; // Track which city's data is currently stored
    private int currentCityIndex = 0; // Track current city index

    static {
        try {
            FileHandler fh = new FileHandler("weatherapp.log", true);
            fh.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fh);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Could not set up logging: " + e.getMessage());
        }
    }

    public WeatherApp() {
        try {
            LOGGER.info("Initializing WeatherApp...");
            this.cityWeatherData = new JSONObject[SUPPORTED_CITIES.length];
            this.tomorrowWeatherData = new JSONObject[SUPPORTED_CITIES.length];

            initializeDatabase();
            createTables();

            // Clear any existing data from previous sessions
            clearAllWeatherData();

            // Initialize with the first city
            fetchWeatherDataForCity(0);
            initializeGUI();

            LOGGER.info("WeatherApp initialized successfully");
        } catch (RuntimeException e) {
            LOGGER.severe("Failed to initialize WeatherApp: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void initializeDatabase() {
        try {
            LOGGER.info("Initializing database connection...");
            Connection tempConnection = DriverManager.getConnection(
                "jdbc:mysql://" + MYSQL_HOST, MYSQL_USER, MYSQL_PASSWORD);

            try (Statement stmt = tempConnection.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + MYSQL_DATABASE);
                LOGGER.info("Database created or verified: " + MYSQL_DATABASE);
            }
            tempConnection.close();

            String url = "jdbc:mysql://" + MYSQL_HOST + "/" + MYSQL_DATABASE
                      + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            this.dbConnection = DriverManager.getConnection(url, MYSQL_USER, MYSQL_PASSWORD);

            if (!this.dbConnection.isValid(5)) {
                throw new SQLException("Failed to establish valid database connection");
            }

            LOGGER.info("Successfully connected to database: " + MYSQL_DATABASE);
        } catch (SQLException e) {
            String errorMsg = "Critical database initialization error: " + e.getMessage();
            LOGGER.severe(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    private void createTables() {
        try {
            LOGGER.info("Checking and creating necessary tables...");
            try (Statement stmt = dbConnection.createStatement()) {
                // Create weather_data table
                String createWeatherTable =
                    "CREATE TABLE IF NOT EXISTS weather_data (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    city VARCHAR(50) NOT NULL," +
                    "    temperature DOUBLE NOT NULL," +
                    "    humidity DOUBLE NOT NULL," +
                    "    wind_speed DOUBLE NOT NULL," +
                    "    date DATE NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    INDEX idx_city_date (city, date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                stmt.executeUpdate(createWeatherTable);

                // Create predictions table with created_at column
                String createPredictionsTable =
                    "CREATE TABLE IF NOT EXISTS predictions (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    city VARCHAR(50) NOT NULL," +
                    "    preds DOUBLE NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    INDEX idx_city_created (city, created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                stmt.executeUpdate(createPredictionsTable);
            }
        } catch (SQLException e) {
            String errorMsg = "Critical error creating tables: " + e.getMessage();
            LOGGER.severe(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }

    private void clearAllWeatherData() {
        try {
            LOGGER.info("Clearing all weather data from previous sessions...");
            try (Statement stmt = dbConnection.createStatement()) {
                stmt.executeUpdate("DELETE FROM weather_data");
                stmt.executeUpdate("DELETE FROM predictions");
            }
            LOGGER.info("Successfully cleared all weather data");
        } catch (SQLException e) {
            LOGGER.severe("Error clearing weather data: " + e.getMessage());
            throw new RuntimeException("Failed to clear weather data", e);
        }
    }

    public void fetchWeatherDataForCity(int cityIndex) {
        this.currentCityIndex = cityIndex;
        String city = SUPPORTED_CITIES[cityIndex];
        LOGGER.info("Fetching weather data for " + city);

        try {
            // First, clear existing data if we have stored data for a different city
            if (currentStoredCity != null && !currentStoredCity.equals(city)) {
                clearCityData(currentStoredCity);
            }

            // Fetch today's weather for display
            JSONObject todayData = fetchWeatherData(city, LocalDate.now());
            if (todayData != null && todayData.has("currentConditions")) {
                cityWeatherData[cityIndex] = todayData.getJSONObject("currentConditions");
                cityWeatherData[cityIndex].put("address", todayData.getString("address"));
                cityWeatherData[cityIndex].put("timezone", todayData.getString("timezone"));
            }

            // Fetch tomorrow's weather data
            JSONObject tomorrowData = fetchWeatherData(city, LocalDate.now().plusDays(1));
            if (tomorrowData != null && tomorrowData.has("days")) {
                JSONArray days = tomorrowData.getJSONArray("days");
                if (days.length() > 0) {
                    tomorrowWeatherData[cityIndex] = days.getJSONObject(0);
                    storeTomorrowWeatherData(city, tomorrowWeatherData[cityIndex]);
                    currentStoredCity = city;
                }
            }

            LOGGER.info("Successfully updated weather data for " + city);
        } catch (Exception e) {
            LOGGER.severe("Failed to fetch/store weather data for " + city + ": " + e.getMessage());
            throw new RuntimeException("Failed to fetch/store weather data", e);
        }
    }

    private void clearCityData(String city) {
        LOGGER.info("Clearing data for city: " + city);
        try {
            String deleteWeatherQuery = "DELETE FROM weather_data WHERE city = ?";
            String deletePredictionsQuery = "DELETE FROM predictions WHERE city = ?";

            dbConnection.setAutoCommit(false);
            try {
                // Delete from weather_data
                try (PreparedStatement stmt = dbConnection.prepareStatement(deleteWeatherQuery)) {
                    stmt.setString(1, city);
                    stmt.executeUpdate();
                }

                // Delete from predictions
                try (PreparedStatement stmt = dbConnection.prepareStatement(deletePredictionsQuery)) {
                    stmt.setString(1, city);
                    stmt.executeUpdate();
                }

                dbConnection.commit();
                LOGGER.info("Successfully cleared data for " + city);
            } catch (SQLException e) {
                dbConnection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.severe("Error clearing data for " + city + ": " + e.getMessage());
            throw new RuntimeException("Failed to clear city data", e);
        } finally {
            try {
                dbConnection.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.warning("Failed to reset auto-commit: " + e.getMessage());
            }
        }
    }

    private void storeTomorrowWeatherData(String city, JSONObject weatherData) {
        String insertQuery = "INSERT INTO weather_data (city, temperature, humidity, wind_speed, date) VALUES (?, ?, ?, ?, ?)";

        try {
            // Verify we have current weather data
            if (cityWeatherData[currentCityIndex] == null) {
                throw new RuntimeException("Current weather data not available");
            }

            // Get today's temperature from current conditions
            double todayTemp = cityWeatherData[currentCityIndex].getDouble("temp");

            try (PreparedStatement insertStmt = dbConnection.prepareStatement(insertQuery)) {
                insertStmt.setString(1, city);
                // Use today's temperature instead of tomorrow's
                insertStmt.setDouble(2, todayTemp);
                // Use tomorrow's humidity and wind speed
                insertStmt.setDouble(3, weatherData.getDouble("humidity"));
                insertStmt.setDouble(4, weatherData.getDouble("windspeed"));
                insertStmt.setString(5, LocalDate.now().plusDays(1).toString());
                insertStmt.executeUpdate();
            }
            LOGGER.info("Successfully stored hybrid weather data for " + city);
        } catch (SQLException e) {
            LOGGER.severe("Error storing weather data for " + city + ": " + e.getMessage());
            throw new RuntimeException("Failed to store weather data", e);
        }
    }

    public static JSONObject fetchWeatherData(String location, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ISO_DATE);
        String urlString = String.format(
            "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/%s/%s?unitGroup=metric&key=%s&contentType=json",
            location, dateStr, API_KEY);

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    return new JSONObject(content.toString());
                }
            } else {
                LOGGER.warning("Failed to fetch weather data. Response code: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            LOGGER.severe("Error fetching weather data: " + e.getMessage());
        }
        return null;
    }

    public void runPredictionForCity(String city) {
        try {
            String pythonPath = "C:\\Users\\shshv\\AppData\\Local\\Programs\\Python\\Python313\\python.exe";
            String scriptPath = "C:\\Users\\shshv\\IdeaProjects\\multi-task\\python1\\" +
                              city.toLowerCase() + ".py";

            ProcessBuilder processBuilder = new ProcessBuilder(pythonPath, scriptPath);
            processBuilder.inheritIO();

            LOGGER.info("Starting prediction process for " + city);
            Process process = processBuilder.start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Temperature prediction completed for " + city);
            } else {
                LOGGER.warning("Prediction failed for " + city + ". Exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.severe("Error running prediction script for " + city + ": " + e.getMessage());
            throw new RuntimeException("Failed to run prediction", e);
        }
    }

    public double getPredictionForCity(String city) {
        String selectQuery = "SELECT preds FROM predictions WHERE city = ?";
        try (PreparedStatement stmt = dbConnection.prepareStatement(selectQuery)) {
            stmt.setString(1, city);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("preds");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Error retrieving prediction for " + city + ": " + e.getMessage());
            throw new RuntimeException("Failed to retrieve prediction", e);
        }
        return 0.0;
    }

    private void initializeGUI() {
        if (cityWeatherData[0] != null && dbConnection != null) {
            gui = new WeatherGUI(this);
            LOGGER.info("GUI initialized successfully");
        } else {
            String errorMsg = "Failed to initialize GUI: Missing weather data or database connection.";
            LOGGER.severe(errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    public String[] getSupportedCities() {
        return SUPPORTED_CITIES;
    }

    public JSONObject getWeatherData(int cityIndex) {
        return cityWeatherData[cityIndex];
    }

    public static void main(String[] args) {
        try {
            new WeatherApp();
        } catch (Exception e) {
            LOGGER.severe("Application failed to start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}