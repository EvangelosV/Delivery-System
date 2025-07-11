package Objects;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Store implements Serializable {
    private static final long serialVersionUID = 1L;

    private String storeName;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private int stars;
    private int noOfVotes;
    private String storeLogo;
    private List<Product> products;

    // Constructors
    public Store() {
        this.products = new ArrayList<>();
    }

    public Store(String storeName, double latitude, double longitude, String foodCategory,
                 int stars, int noOfVotes, String storeLogo) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.storeLogo = storeLogo;
        this.products = new ArrayList<>();
    }

    // Getters and Setters
    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getFoodCategory() {
        return foodCategory;
    }

    public void setFoodCategory(String foodCategory) {
        this.foodCategory = foodCategory;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public int getNoOfVotes() {
        return noOfVotes;
    }

    public void setNoOfVotes(int noOfVotes) {
        this.noOfVotes = noOfVotes;
    }

    public String getStoreLogo() {
        return storeLogo;
    }

    public void setStoreLogo(String storeLogo) {
        this.storeLogo = storeLogo;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }

    public Optional<Product> findProductByName(String productName) {
        return products.stream()
                .filter(p -> p.getProductName().equalsIgnoreCase(productName))
                .findFirst();
    }

    @Override
    public String toString() {
        return String.format("%s - %s - Rating: %d/5 (%d votes)",
                storeName, foodCategory, stars, noOfVotes);
    }

    public void insertStore(String jsonPath) {
        try {
            // Read the JSON file
            String content = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(jsonPath)), java.nio.charset.StandardCharsets.UTF_8);

            // Parse basic store information
            this.storeName = extractValue(content, "StoreName");
            this.latitude = Double.parseDouble(extractValue(content, "Latitude"));
            this.longitude = Double.parseDouble(extractValue(content, "Longitude"));
            this.foodCategory = extractValue(content, "FoodCategory");
            this.stars = Integer.parseInt(extractValue(content, "Stars"));
            this.noOfVotes = Integer.parseInt(extractValue(content, "NoOfVotes"));
            this.storeLogo = extractValue(content, "StoreLogo");

            // Initialize products list if null
            if (this.products == null) {
                this.products = new ArrayList<>();
            } else {
                this.products.clear(); // Clear existing products
            }

            // Extract products array
            String productsSection = content.substring(content.indexOf("\"Products\": [") + 13);
            productsSection = productsSection.substring(0, productsSection.lastIndexOf("]"));

            // Split products by their JSON object boundaries
            List<String> productJsons = splitProductJson(productsSection);

            // Process each product
            for (String productJson : productJsons) {
                Product product = new Product();
                product.setProductName(extractValue(productJson, "ProductName"));
                product.setProductType(extractValue(productJson, "ProductType"));
                product.setAvailableAmount(Integer.parseInt(extractValue(productJson, "Available Amount")));
                product.setPrice(Double.parseDouble(extractValue(productJson, "Price")));

                // Add to store's products list
                this.products.add(product);
            }

            System.out.println("Successfully loaded store " + storeName + " with " + products.size() + " products");

        } catch (IOException e) {
            System.err.println("Error reading file: " + jsonPath);
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error parsing store data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractValue(String json, String key) {
        String searchString = "\"" + key + "\": ";
        int startIndex = json.indexOf(searchString) + searchString.length();

        // Handle string value (with quotes)
        if (json.charAt(startIndex) == '"') {
            int endIndex = json.indexOf("\"", startIndex + 1);
            return json.substring(startIndex + 1, endIndex);
        }
        // Handle numeric value (without quotes)
        else {
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) {
                // Could be the last property in the object
                endIndex = json.indexOf("}", startIndex);
            }
            return json.substring(startIndex, endIndex).trim();
        }
    }

    private List<String> splitProductJson(String productsJson) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int startIndex = 0;

        for (int i = 0; i < productsJson.length(); i++) {
            char c = productsJson.charAt(i);

            if (c == '{') {
                if (depth == 0) {
                    startIndex = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Found complete JSON product
                    String productJson = productsJson.substring(startIndex, i + 1);
                    result.add(productJson);
                }
            }
        }
        return result;
    }
}