package org.example;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import org.json.JSONObject;

public class WeatherGUI extends Frame {
    private Image backgroundImage;
    private int currentCityIndex = 0;
    private WeatherApp weatherApp;
    private String[] cities;
    private double predictedTemp = 0.0;
    private boolean isPredictionLoading = false;
    private boolean isWeatherUpdating = false;

    public WeatherGUI(WeatherApp app) {
        this.weatherApp = app;
        this.cities = app.getSupportedCities();

        setSize(800, 600);
        setTitle("Weather Application");
        setLayout(null);
        centerWindow();

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                System.exit(0);
            }
        });

        createButtons();
        updateDisplay();
        setVisible(true);
    }

    private void createButtons() {
        Button predictButton = new Button("Predict Tomorrow's Weather");
        predictButton.setBounds(300, getHeight() - 100, 200, 40);
        predictButton.setFont(new Font("Arial", Font.BOLD, 16));
        predictButton.setBackground(new Color(100, 149, 237));
        predictButton.setForeground(Color.WHITE);
        predictButton.addActionListener(e -> handlePrediction());
        add(predictButton);

        Button nextButton = new Button("→");
        Button prevButton = new Button("←");

        nextButton.setBounds(getWidth() - 80, 40, 50, 50);
        prevButton.setBounds(30, 40, 50, 50);

        for (Button btn : new Button[]{nextButton, prevButton}) {
            btn.setFont(new Font("Arial", Font.BOLD, 24));
            btn.setBackground(new Color(100, 149, 237, 200));
            btn.setForeground(Color.WHITE);
        }

        nextButton.addActionListener(e -> handleCityChange(1));
        prevButton.addActionListener(e -> handleCityChange(-1));

        add(nextButton);
        add(prevButton);
    }

    private void handleCityChange(int direction) {
        if (isWeatherUpdating) {
            return; // Prevent multiple simultaneous updates
        }

        int newIndex = currentCityIndex + direction;
        if (newIndex >= 0 && newIndex < cities.length) {
            isWeatherUpdating = true;
            currentCityIndex = newIndex;

            // Update UI to show loading state
            repaint();

            new Thread(() -> {
                try {
                    // Fetch and store new city's data (this will also clear previous city's data)
                    weatherApp.fetchWeatherDataForCity(currentCityIndex);

                    // Update display on EDT
                    EventQueue.invokeLater(() -> {
                        updateDisplay();
                        isWeatherUpdating = false;
                        // Reset prediction state
                        predictedTemp = 0.0;
                        isPredictionLoading = false;
                        repaint();
                    });
                } catch (Exception ex) {
                    EventQueue.invokeLater(() -> {
                        isWeatherUpdating = false;
                        // Show error message to user
                        showErrorDialog("Error updating weather data: " + ex.getMessage());
                        repaint();
                    });
                }
            }).start();
        }
    }

    private void showErrorDialog(String message) {
        Dialog dialog = new Dialog(this, "Error", true);
        dialog.setLayout(new BorderLayout());
        dialog.add(new Label(message), BorderLayout.CENTER);
        Button okButton = new Button("OK");
        okButton.addActionListener(e -> dialog.dispose());
        dialog.add(okButton, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void handlePrediction() {
        if (isPredictionLoading || isWeatherUpdating) {
            return; // Prevent multiple simultaneous predictions
        }

        isPredictionLoading = true;
        repaint();

        new Thread(() -> {
            try {
                String currentCity = cities[currentCityIndex];
                weatherApp.runPredictionForCity(currentCity);
                predictedTemp = weatherApp.getPredictionForCity(currentCity);

                EventQueue.invokeLater(() -> {
                    isPredictionLoading = false;
                    repaint();
                });
            } catch (Exception ex) {
                EventQueue.invokeLater(() -> {
                    isPredictionLoading = false;
                    showErrorDialog("Error making prediction: " + ex.getMessage());
                    repaint();
                });
            }
        }).start();
    }

    private void updateDisplay() {
        JSONObject weatherData = weatherApp.getWeatherData(currentCityIndex);
        if (weatherData != null) {
            updateBackgroundBasedOnCondition(weatherData.optString("conditions", "clear"));
            setTitle("Weather Application - " + cities[currentCityIndex]);
            repaint();
        }
    }

    private void updateBackgroundBasedOnCondition(String condition) {
        String imagePath;
        condition = condition.toLowerCase();
        if (condition.contains("cloud")) {
            imagePath = "C:\\Users\\shshv\\IdeaProjects\\multi-task\\java\\src\\main\\resources\\anandu-vinod-pbxwxwfI0B4-unsplash.jpg";
        } else if (condition.contains("rain")) {
            imagePath = "C:\\Users\\shshv\\IdeaProjects\\multi-task\\java\\src\\main\\resources\\vecteezy_ai-generated-rainy-sky-observations-background_42195747.jpg";
        } else {
            imagePath = "C:\\Users\\shshv\\IdeaProjects\\multi-task\\java\\src\\main\\resources\\drew-hays-_Vq7JTlS4XE-unsplash.jpg";
        }

        try {
            backgroundImage = ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            System.err.println("Error loading background image: " + e.getMessage());
        }
    }

    private void centerWindow() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - getWidth()) / 2;
        int y = (screenSize.height - getHeight()) / 2;
        setLocation(x, y);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;

        // Draw background
        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        }

        // Draw loading indicator if updating
        if (isWeatherUpdating) {
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            String loadingText = "Updating weather data...";
            FontMetrics metrics = g2d.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(loadingText)) / 2;
            drawTransparentLabel(g2d, loadingText, x, getHeight() / 2);
            return; // Don't draw weather info while updating
        }

        // Draw weather information
        JSONObject weatherData = weatherApp.getWeatherData(currentCityIndex);
        if (weatherData != null) {
            drawWeatherInfo(g2d, weatherData);
        }
    }

    private void drawWeatherInfo(Graphics2D g2d, JSONObject weatherData) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw city name with larger font and better positioning
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        FontMetrics cityMetrics = g2d.getFontMetrics();
        String cityName = cities[currentCityIndex];
        int cityX = (getWidth() - cityMetrics.stringWidth(cityName)) / 2;
        drawTransparentLabel(g2d, cityName, cityX, 100);

        // Draw current temperature with larger font
        g2d.setFont(new Font("Arial", Font.BOLD, 64));
        String tempText = String.format("%.1f°C", weatherData.optDouble("temp", 0.0));
        FontMetrics tempMetrics = g2d.getFontMetrics();
        int tempX = (getWidth() - tempMetrics.stringWidth(tempText)) / 2;
        drawTransparentLabel(g2d, tempText, tempX, getHeight() / 3 + 30);

        // Draw weather details with improved spacing
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        String[] details = {
            String.format("Humidity: %.1f%%", weatherData.optDouble("humidity", 0.0)),
            String.format("Wind Speed: %.1f km/h", weatherData.optDouble("windspeed", 0.0)),
            "Condition: " + weatherData.optString("conditions", "Not available")
        };

        int detailsY = getHeight() / 2;
        for (String detail : details) {
            FontMetrics metrics = g2d.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(detail)) / 2;
            drawTransparentLabel(g2d, detail, x, detailsY);
            detailsY += 35;
        }

        // Draw prediction status or result
        if (isPredictionLoading) {
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            String loadingText = "Calculating prediction...";
            FontMetrics metrics = g2d.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(loadingText)) / 2;
            drawTransparentLabel(g2d, loadingText, x, getHeight() - 150);
        } else if (predictedTemp > 0) {
            g2d.setFont(new Font("Arial", Font.BOLD, 22));
            String predictionText = String.format("Tomorrow's Predicted Temperature: %.1f°C", predictedTemp);
            FontMetrics metrics = g2d.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(predictionText)) / 2;
            drawTransparentLabel(g2d, predictionText, x, getHeight() - 150);
        }
    }

    private void drawTransparentLabel(Graphics2D g2d, String text, int x, int y) {
        FontMetrics metrics = g2d.getFontMetrics();
        int width = metrics.stringWidth(text) + 30;
        int height = metrics.getHeight() + 15;

        // Draw semi-transparent background
        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.fillRoundRect(x - 15, y - metrics.getAscent() - 7, width, height, 20, 20);

        // Draw text
        g2d.setColor(new Color(0, 0, 0, 230));
        g2d.drawString(text, x, y);
    }
}