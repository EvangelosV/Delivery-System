package Worker;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


import Objects.Store;
import Objects.Product;

/**
 * Handles individual worker connections to the server.
 * Each connection is processed in its own thread.
 */
class WorkerHandler extends Thread {
    private Socket masterSocket;
    private String workerId;
    private static final String STORES_DIR = "data\\stores";
    private Socket reducerSocket;
    private ObjectOutputStream out; // for master
    private ObjectInputStream in;
    private ObjectOutputStream reducerOut;
    private ObjectInputStream reducerIn;
    private static final Object reducerLock = new Object();

    // Static map to store Store objects in memory, shared across all handler instances for this worker
    private static final Map<String, Store> storeCache = Collections.synchronizedMap(new HashMap<>());
    
    // Map to track sales data - resets when the system restarts
    private static final Map<String, int[]> salesData = Collections.synchronizedMap(new HashMap<>());

    public WorkerHandler(Socket masterSocket, Socket reducerSocket, String workerId) throws IOException {
        this.masterSocket = masterSocket;
        this.workerId = workerId;
        this.reducerSocket = reducerSocket;

        this.out = new ObjectOutputStream(masterSocket.getOutputStream());
        this.in = new ObjectInputStream(masterSocket.getInputStream());

        this.reducerOut = new ObjectOutputStream(reducerSocket.getOutputStream());
        this.reducerIn = new ObjectInputStream(reducerSocket.getInputStream());
        reducerOut.writeObject("Worker established connection with Reducer");
        reducerOut.flush();
    }

    @Override
    public void run() {
        try {
            String command = (String) in.readObject();
            System.out.println("\n[Worker " + workerId + "] Received command: " + command);

            String response = "Unknown command response";

            // Handle different command types
            if (command.startsWith("addStore")) {
                try {
                    // Modified to receive Store object instead of String
                    Store storeData = (Store) in.readObject();
                    String storeName = command.substring("addStore ".length()).trim();
                    System.out.println("[Worker " + workerId + "] Adding store: " + storeName);

                    // Save store object in memory cache
                    storeCache.put(storeName, storeData);

                    // Process store data for file storage
                    String storeJson = storeToJson(storeData);
                    boolean success = saveStoreData(storeName, storeJson);

                    // Send response back to Master
                    String responseMsg = success ?
                            "Worker successfully added store: " + storeName :
                            "Worker failed to add store: " + storeName;

                    System.out.println("[Worker " + workerId + "] Store cache now contains: " +
                            storeCache.keySet());

                    System.out.println("[Worker " + workerId + "] Sending response: " + responseMsg);
                    out.writeObject(responseMsg);
                    out.flush();
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing addStore: " + e.getMessage());
                    e.printStackTrace();
                    out.writeObject("Error: " + e.getMessage());
                    out.flush();
                }
            } else if (command.startsWith("updateStock")) {
                try {
                    // Read payload as List<Object>
                    @SuppressWarnings("unchecked")
                    List<Object> payload = (List<Object>) in.readObject();
                    System.out.println("[Worker " + workerId + "] Processing updateStock operation");

                    // Extract data from payload
                    Product selectedProduct = (Product) payload.get(0);
                    Boolean isAddOperation = (Boolean) payload.get(1);
                    int quantity = (int) payload.get(2);

                    // Process the update stock operation using the in-memory store
                    boolean success = updateProductStock(selectedProduct, isAddOperation, quantity);

                    // Send response back to Master
                    response = success ?
                            "Worker successfully updated stock for product: " + selectedProduct.getProductName() :
                            "Worker failed to update stock for product: " + selectedProduct.getProductName();

                    System.out.println("[Worker " + workerId + "] " + response);
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing updateStock: " + e.getMessage());
                    e.printStackTrace();
                    response = "Error processing updateStock: " + e.getMessage();
                }
            } else if (command.startsWith("addProduct")) {
                try {
                    // Read product object
                    Product newProduct = (Product) in.readObject();
                    System.out.println("[Worker " + workerId + "] Processing addProduct operation");

                    // Process the add product operation using in-memory store
                    boolean success = addProductToStore(newProduct);

                    // Send response back to Master
                    response = success ?
                            "Worker successfully added product: " + newProduct.getProductName() :
                            "Worker failed to add product: " + newProduct.getProductName();

                    System.out.println("[Worker " + workerId + "] " + response);
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing addProduct: " + e.getMessage());
                    e.printStackTrace();
                    response = "Error processing addProduct: " + e.getMessage();
                }
            } else if (command.startsWith("removeProduct")) {
                try {
                    // Read product object
                    Product productToRemove = (Product) in.readObject();
                    System.out.println("[Worker " + workerId + "] Processing removeProduct operation");

                    // Process the remove product operation using in-memory store
                    boolean success = removeProductFromStore(productToRemove);

                    // Send response back to Master
                    response = success ?
                            "Worker successfully removed product: " + productToRemove.getProductName() :
                            "Worker failed to remove product: " + productToRemove.getProductName();

                    System.out.println("[Worker " + workerId + "] " + response);
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing removeProduct: " + e.getMessage());
                    e.printStackTrace();
                    response = "Error processing removeProduct: " + e.getMessage();
                }
            } else if (command.startsWith("buy")) {
                try {
                    // Buy command format: buy|storeName|productName|quantity
                    // Read payload as List<Object>
                    @SuppressWarnings("unchecked")
                    List<Object> payload = (List<Object>) in.readObject();

                    if (payload.size() >= 3) {
                        String storeName = (String) payload.get(0);
                        String productName = (String) payload.get(1);
                        int quantity = (int) payload.get(2);

                        // Process the purchase using in-memory store
                        response = processPurchase(storeName, productName, quantity);
                    } else {
                        response = "Invalid purchase payload";
                    }
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing buy: " + e.getMessage());
                    e.printStackTrace();
                    response = "Error processing purchase: " + e.getMessage();
                }
            } else if (command.equals("getStoreInfo")) {
                try {
                    String storeName = (String) in.readObject();
                    System.out.println("[Worker " + workerId + "] Getting info for store: " + storeName);

                    // Check if store exists in our cache
                    if (storeCache.containsKey(storeName)) {
                        Store store = storeCache.get(storeName);
                        System.out.println("[Worker " + workerId + "] Found store in cache: " + storeName);
                        out.writeObject(store);
                        out.flush();
                    } else {
                        System.out.println("[Worker " + workerId + "] Store not found in cache: " + storeName);
                        out.writeObject("Store not found");
                        out.flush();
                    }
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing getStoreInfo: " + e.getMessage());
                    e.printStackTrace();
                    out.writeObject("Error: " + e.getMessage());
                    out.flush();
                }
            } else if (command.equals("getStoreProducts")) {
                try {
                    // Read store name from input stream
                    String storeName = (String) in.readObject();
                    System.out.println("[Worker " + workerId + "] Getting products for store: " + storeName);
                    
                    // Use our helper method to get only visible products
                    response = getStoreVisibleProducts(storeName);
                } catch (Exception e) {
                    System.err.println("[Worker " + workerId + "] Error processing getStoreProducts: " + e.getMessage());
                    e.printStackTrace();
                    response = "Error: " + e.getMessage();
                }
            } else if (command.startsWith("findStores")) {
                // Use our dedicated method for finding nearby stores
                response = findNearbyStores(command);
            } else if (command.startsWith("search")) {
                // Use our dedicated method for searching products
                response = searchProducts(command);
            } else if (command.startsWith("getSalesByCategory")) {
                // Use our dedicated method for getting sales data
                response = getSalesByFoodCategory(command);
            } else if (command.startsWith("getSalesByProduct")) {
                // Use our dedicated method for getting product sales data
                response = getSalesByProduct(command);
            } else {
                // Generic processing for unknown commands
                response = "Unknown command: " + command;
                System.err.println("[Worker " + workerId + "] " + response);
            }

            // Send the response back to Master
            System.out.println("[Worker " + workerId + "] Sending response: " + response);
            out.writeObject(response);
            out.flush();

            // Send processing results to the Reducer for aggregation
            sendResultsToReducer(command, response);

        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error processing request: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                masterSocket.close();
                System.out.println("[Worker " + workerId + "] Connection closed");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Send processing results to the Reducer for aggregation
     */
    private void sendResultsToReducer(String request, String response) {
        System.out.println("[Worker " + workerId + "] Sending results to Reducer...");
        try {
            // Create a map with processing results
            Map<String, Object> results = new HashMap<>();
            results.put("workerId", workerId);
            results.put("timestamp", System.currentTimeMillis());
            results.put("requestType", getRequestType(request));
            results.put("processingTime", new Random().nextInt(100) + 1); // Simulated processing time in ms

            // If this is a purchase, add sales data
            if (request.startsWith("buy")) {
                // Format: buy|storeName|productName|quantity
                String[] parts = request.split("\\|");
                if (parts.length >= 4) {
                    String storeName = parts[1];
                    String productName = parts[2];
                    int quantity = Integer.parseInt(parts[3]);

                    results.put("storeName", storeName);
                    results.put("productName", productName);
                    results.put("quantity", quantity);
                    results.put("success", response.startsWith("Success"));
                }
            }

            // Send the map result to the Reducer
            System.out.println("[Worker " + workerId + "] Sending results to Reducer: " + results);
            Object ack;

            synchronized (reducerLock) {
                reducerOut.writeObject("mapResult");
                reducerOut.writeObject(results);
                reducerOut.flush();
                // Get acknowledgment
                ack = reducerIn.readObject();
            }

            System.out.println("[Worker " + workerId + "] Reducer response: " + ack);

        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error sending results to Reducer: " + e.getMessage());
            // Don't fail the whole operation if Reducer is not available
            System.err.println("[Worker " + workerId + "] Continuing without sending to Reducer");
        }
    }


    // Add method to convert Store object back to JSON
    private String storeToJson(Store store) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("    \"StoreName\": \"").append(store.getStoreName()).append("\",\n");
        json.append("    \"Latitude\": ").append(store.getLatitude()).append(",\n");
        json.append("    \"Longitude\": ").append(store.getLongitude()).append(",\n");
        json.append("    \"FoodCategory\": \"").append(store.getFoodCategory()).append("\",\n");
        json.append("    \"Stars\": ").append(store.getStars()).append(",\n");
        json.append("    \"NoOfVotes\": ").append(store.getNoOfVotes()).append(",\n");
        json.append("    \"StoreLogo\": \"").append(store.getStoreLogo()).append("\",\n");

        // Add products array
        json.append("    \"Products\": [\n");
        List<Product> products = store.getProducts();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            json.append("        {\n");
            json.append("            \"ProductName\": \"").append(product.getProductName()).append("\",\n");
            json.append("            \"ProductType\": \"").append(product.getProductType()).append("\",\n");
            json.append("            \"Available Amount\": ").append(product.getAvailableAmount()).append(",\n");
            json.append("            \"Price\": ").append(product.getPrice()).append("\n");
            json.append("        }");

            // Add comma if not the last product
            if (i < products.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("    ]\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Save store data to a JSON file
     */
    private boolean saveStoreData(String storeName, String storeData) {
        try {
            // Ensure the stores directory exists
            Path storeDir = Paths.get(STORES_DIR);
            if (!Files.exists(storeDir)) {
                Files.createDirectories(storeDir);
                System.out.println("[Worker " + workerId + "] Created directory: " + storeDir);
            }

            // Write the store data to a file
            Path storeFile = storeDir.resolve(storeName + ".json");
            Files.write(storeFile, storeData.getBytes(StandardCharsets.UTF_8));

            System.out.println("[Worker " + workerId + "] Store data saved to: " + storeFile.toAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("[Worker " + workerId + "] Error saving store data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Process a purchase request - memory only, no file updates
     */
    private String processPurchase(String storeName, String productName, int quantity) {
        try {
            System.out.println("[Worker " + workerId + "] Processing purchase: " +
                    storeName + "/" + productName + " x" + quantity);

            // Check if store exists in our cache
            if (!storeCache.containsKey(storeName)) {
                return "Error: Store '" + storeName + "' not found";
            }

            Store store = storeCache.get(storeName);

            // Find the product
            Product targetProduct = null;
            for (Product product : store.getProducts()) {
                if (product.getProductName().equals(productName)) {
                    targetProduct = product;
                    break;
                }
            }

            if (targetProduct == null) {
                return "Error: Product '" + productName + "' not found in store '" + storeName + "'";
            }

            // Check if enough stock
            if (targetProduct.getAvailableAmount() < quantity) {
                return "Error: Insufficient stock. Requested: " + quantity + ", Available: " +
                        targetProduct.getAvailableAmount();
            }

            // Update stock - memory only
            int newStock = targetProduct.getAvailableAmount() - quantity;
            targetProduct.setAvailableAmount(newStock);

            // Calculate price
            double totalPrice = targetProduct.getPrice() * quantity;

            // Update sales tracking
            String salesKey = storeName + ":" + productName;
            int[] currentSales;
            synchronized (salesData) {
                currentSales = salesData.getOrDefault(salesKey, new int[]{0, 0});
                currentSales[0] += quantity;                             // Units sold
                currentSales[1] += (int) (quantity * targetProduct.getPrice()); // Revenue (as int for simplicity)
                salesData.put(salesKey, currentSales);
            }

            System.out.println("[Worker " + workerId + "] Purchase completed and sales data updated: " +
                    quantity + " " + productName + " from " + storeName);
            System.out.println("[Worker " + workerId + "] Total sales for this product: " + currentSales[0]);

            return "Success: Purchased " + quantity + " " + productName + " for " +
                    String.format("%.2f", totalPrice) + "EUR. Remaining stock: " + newStock;

        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error processing purchase: " + e.getMessage());
            e.printStackTrace();
            return "Error processing purchase: " + e.getMessage();
        }
    }


    /**
     * Extract the request type from the request string
     */
    private String getRequestType(String request) {
        if (request.startsWith("buy")) return "purchase";
        if (request.startsWith("search")) return "search";
        if (request.startsWith("findStores")) return "findStores";
        if (request.startsWith("addStore")) return "addStore";
        if (request.startsWith("getStoreInfo")) return "getStoreInfo";
        if (request.startsWith("updateStock")) return "updateStock";
        if (request.startsWith("addProduct")) return "addProduct";
        if (request.startsWith("removeProduct")) return "removeProduct";
        if (request.startsWith("getSalesByCategory")) return "getSalesByCategory";
        if (request.startsWith("getSalesByProduct")) return "getSalesByProduct";
        return "unknown";
    }

    /**
     * Update the stock of a product in the store.
     */
    private boolean updateProductStock(Product productData, boolean isAddOperation, int quantity) {
        try {
            String storeName = productData.getStore().getStoreName();
            String productName = productData.getProductName();

            System.out.println("[Worker " + workerId + "] Updating stock for " +
                    productName + " in store " + storeName +
                    (isAddOperation ? " adding " : " removing ") + quantity);

            // Check if store exists in our cache
            if (!storeCache.containsKey(storeName)) {
                System.err.println("[Worker " + workerId + "] Store not in cache: " + storeName);
                return false;
            }

            Store store = storeCache.get(storeName);

            // Find the product
            Product targetProduct = null;
            for (Product product : store.getProducts()) {
                if (product.getProductName().equals(productName)) {
                    targetProduct = product;
                    break;
                }
            }

            if (targetProduct == null) {
                System.err.println("[Worker " + workerId + "] Product not found: " + productName);
                return false;
            }

            // Get current amount
            int currentAmount = targetProduct.getAvailableAmount();
            int newAmount;

            // Update the amount
            if (isAddOperation) {
                newAmount = currentAmount + quantity;
            } else {
                // Check if enough stock to remove
                if (currentAmount < quantity) {
                    System.err.println("[Worker " + workerId + "] Not enough stock to remove. Current: " +
                            currentAmount + ", Remove: " + quantity);
                    return false;
                }
                newAmount = currentAmount - quantity;
            }

            // Update product
            synchronized (store) {
                targetProduct.setAvailableAmount(newAmount);
            }

            System.out.println("[Worker " + workerId + "] Updated " + productName + " stock: " +
                    currentAmount + " -> " + newAmount);
            return true;
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error updating product stock: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Add a new product to the store.
     */
    private boolean addProductToStore(Product newProduct) {
        try {
            String storeName = newProduct.getStore().getStoreName();

            System.out.println("[Worker " + workerId + "] Adding product " +
                    newProduct.getProductName() + " to store " + storeName);

            // Check if store exists in our cache
            if (!storeCache.containsKey(storeName)) {
                System.err.println("[Worker " + workerId + "] Store not in cache: " + storeName);
                return false;
            }

            Store store = storeCache.get(storeName);

            // Check if product with same name already exists
            for (Product existingProduct : store.getProducts()) {
                if (existingProduct.getProductName().equals(newProduct.getProductName())) {
                    // If the product exists but is invisible and the new product is meant to be visible,
                    // make the existing product visible and update its properties
                    if (!existingProduct.getVisible() && newProduct.getVisible()) {
                        System.out.println("[Worker " + workerId + "] Restoring hidden product: " + 
                                           newProduct.getProductName());
                        existingProduct.setVisible(true);
                        existingProduct.setAvailableAmount(newProduct.getAvailableAmount());
                        existingProduct.setPrice(newProduct.getPrice());
                        existingProduct.setProductType(newProduct.getProductType());
                        
                        // Products are now only stored in memory, no need to save to JSON
                        System.out.println("[Worker " + workerId + "] Product " + newProduct.getProductName() + 
                                          " restored and kept in memory only");
                        
                        return true;
                    } else if (existingProduct.getVisible()) {
                        // If product exists and is already visible, return failure
                        System.err.println("[Worker " + workerId + "] Product already exists: " +
                                newProduct.getProductName());
                        return false;
                    }
                }
            }

            // Add the product to the store
            newProduct.setStore(store);
            synchronized (store) {
                store.getProducts().add(newProduct);
            }
            
            // Products are now only stored in memory, no need to save to JSON
            System.out.println("[Worker " + workerId + "] Added product " +
                    newProduct.getProductName() + " to store " + storeName + " (in memory only)");
            return true;
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error adding product: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Remove a product from the store.
     */
        private boolean removeProductFromStore(Product productToRemove) {
        try {
            String storeName = productToRemove.getStore().getStoreName();
            String productName = productToRemove.getProductName();

            System.out.println("[Worker " + workerId + "] Hiding product " +
                    productName + " from store " + storeName);

            // Check if store exists in our cache
            if (!storeCache.containsKey(storeName)) {
                System.err.println("[Worker " + workerId + "] Store not in cache: " + storeName);
                return false;
            }

            Store store = storeCache.get(storeName);

            // Find the product and set its visibility to false
            boolean found = false;
            for (Product product : store.getProducts()) {
                if (product.getProductName().equals(productName)) {
                    synchronized (store) {
                        product.setVisible(false);
                    }
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.err.println("[Worker " + workerId + "] Product not found: " + productName);
                return false;
            }

            System.out.println("[Worker " + workerId + "] Product " +
                    productName + " set to invisible in store " + storeName);

            return true;
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error hiding product: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Find nearby stores based on criteria from the command
     * Format: findStores|latitude|longitude|radius|filterType|foodCategory|minStars|maxPriceRating
     */
    private String findNearbyStores(String command) {
        try {
            System.out.println("[Worker " + workerId + "] Processing findStores command");
            
            // Parse the search parameters 
            String[] parts = command.split("\\|");
            if (parts.length < 4) {
                return "Invalid search format";
            }
            
            // Extract search criteria
            double customerLat = Double.parseDouble(parts[1]);
            double customerLon = Double.parseDouble(parts[2]);
            double radius = Double.parseDouble(parts[3]);
            
            // Get filter parameters if provided
            String filterType = parts.length > 4 ? parts[4] : "none";
            String foodCategoryFilter = parts.length > 5 ? parts[5].toLowerCase() : "";
            int minStarFilter = parts.length > 6 ? Integer.parseInt(parts[6]) : 0;
            int maxPriceRatingFilter = parts.length > 7 ? Integer.parseInt(parts[7]) : 3;
            
            System.out.println("[Worker " + workerId + "] Search parameters: lat=" + customerLat + 
                              ", lon=" + customerLon + ", radius=" + radius + ", filter=" + filterType);
            
            StringBuilder results = new StringBuilder();
            int matchedStores = 0;
            
            // Check each store in our cache for matches
            synchronized (storeCache) {
                for (String storeName : storeCache.keySet()) {
                    Store store = storeCache.get(storeName);

                    // Skip if store is null
                    if (store == null) continue;

                    // Calculate distance using Haversine formula
                    double distance = calculateDistance(customerLat, customerLon,
                                                       store.getLatitude(), store.getLongitude());

                    // Get store properties for filtering
                    String foodCategory = store.getFoodCategory().toLowerCase();
                    int stars = store.getStars();

                    // Calculate average price for price filtering
                    double avgPrice = calculateAveragePrice(store);
                    int priceRating = getPriceRating(avgPrice);
                    String priceRatingSymbol = "$".repeat(priceRating);

                    System.out.println("[Worker " + workerId + "] Store: " + storeName +
                                      ", Distance: " + distance + " km, Category: " + foodCategory +
                                      ", Stars: " + stars + ", Price Rating: " + priceRatingSymbol);

                    // Check if store passes filtering
                    boolean passesFilter = true;

                    switch(filterType) {
                        case "category":
                            passesFilter = foodCategory.contains(foodCategoryFilter);
                            break;
                        case "stars":
                            passesFilter = stars >= minStarFilter;
                            break;
                        case "price":
                            passesFilter = priceRating <= maxPriceRatingFilter;
                            break;
                        case "none":
                        default:
                            // No filtering needed
                            break;
                    }

                    // Add to results if the store is within radius and passes filter
                    if (distance <= radius && passesFilter) {
                        if (matchedStores > 0) {
                            results.append("|");
                        }
                    }

                    // Format: StoreName,FoodCategory,Distance,Stars,AvgPrice,PriceRating
                    results.append(String.format("%s,%s,%.2f,%d,%.2f,%s",
                                  store.getStoreName(), foodCategory, distance,
                                  stars, avgPrice, priceRatingSymbol));

                    matchedStores++;
                    System.out.println("[Worker " + workerId + "] Store matched: " + store.getStoreName());
                }
            }
            
            return matchedStores > 0 ? results.toString() : "No stores found";
            
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error finding nearby stores: " + e.getMessage());
            e.printStackTrace();
            return "Error processing search: " + e.getMessage();
        }
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // Earth radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c; // Distance in kilometers
    }
    
    /**
     * Calculate average price of products in a store
     */
    private double calculateAveragePrice(Store store) {
        List<Product> products = store.getProducts();
        if (products == null || products.isEmpty()) {
            return 0.0;
        }
        
        double totalPrice = 0.0;
        int visibleProductCount = 0;
        
        for (Product product : products) {
            if (product.getVisible()) {
                totalPrice += product.getPrice();
                visibleProductCount++;
            }
        }
        
        return visibleProductCount > 0 ? totalPrice / visibleProductCount : 0.0;
    }
    
    /**
     * Determine the price rating based on average price
     */
    private int getPriceRating(double avgPrice) {
        if (avgPrice <= 5.0) {
            return 1; // $
        } else if (avgPrice <= 15.0) {
            return 2; // $$
        } else {
            return 3; // $$$
        }
    }

    /**
     * Search for products based on search term
     */
    private String searchProducts(String command) {
        try {
            // Extract search term from command
            String searchTerm = "";
            if (command.startsWith("search ")) {
                searchTerm = command.substring("search ".length()).trim().toLowerCase();
            }
            
            System.out.println("[Worker " + workerId + "] Searching for products with term: \"" + searchTerm + "\"");
            
            StringBuilder results = new StringBuilder("Products matching \"" + searchTerm + "\":\n");
            int productCount = 0;
            
            // Search through all stores in this worker's cache
            synchronized (storeCache) {
                for (String storeName : storeCache.keySet()) {
                    Store store = storeCache.get(storeName);
                    if (store == null) continue;

                    // Search through visible products in this store
                    for (Product product : store.getProducts()) {
                        // Skip invisible products
                        if (!product.getVisible()) continue;

                        String productName = product.getProductName().toLowerCase();
                        String productType = product.getProductType().toLowerCase();

                        // If no search term or product matches search
                        if (searchTerm.isEmpty() ||
                                productName.contains(searchTerm) ||
                                productType.contains(searchTerm)) {

                            results.append(String.format("%d. %s - %s - Price: %.2f - Available: %d - Store: %s\n",
                                    ++productCount, product.getProductName(), product.getProductType(),
                                    product.getPrice(), product.getAvailableAmount(), storeName));
                        }
                    }
                }
            }
            
            return productCount > 0 ? results.toString() : "No products found matching \"" + searchTerm + "\".";
            
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error searching products: " + e.getMessage());
            e.printStackTrace();
            return "Error processing product search: " + e.getMessage();
        }
    }

    /**
     * Get sales data for stores in a particular food category
     */
    private String getSalesByFoodCategory(String command) {
        try {
            // Extract food category from command
            String foodCategory = "";
            if (command.startsWith("getSalesByCategory ")) {
                foodCategory = command.substring("getSalesByCategory ".length()).trim().toLowerCase();
            }
            
            System.out.println("[Worker " + workerId + "] Getting sales data for food category: " + foodCategory);
            
            StringBuilder results = new StringBuilder();
            int totalSalesCount = 0;
            int storeCount = 0;
            
            // Check each store in this worker's cache
            synchronized (storeCache) {
                for (String storeName : storeCache.keySet()) {
                    Store store = storeCache.get(storeName);
                    if (store == null) continue;

                    String storeCategory = store.getFoodCategory().toLowerCase();

                    // Only process stores matching the requested food category
                    if (storeCategory.contains(foodCategory)) {
                        System.out.println("[Worker " + workerId + "] Found matching store: " + storeName);

                        // Calculate simulated sales for this store
                        int storeSales = calculateStoreSales(store);

                        if (storeSales > 0) {
                            // Add to results if there were any sales
                            if (storeCount > 0) {
                                results.append("|");
                            }

                            // Format: StoreName:SalesCount
                            results.append(storeName).append(":").append(storeSales);

                            // Add to total
                            totalSalesCount += storeSales;
                            storeCount++;
                        }
                    }
                }
            }
            // Add total sales to the results
            if (storeCount > 0) {
                results.append("|Total:").append(totalSalesCount);
                return results.toString();
            } else {
                return "No sales data found for category: " + foodCategory;
            }
            
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error getting sales data: " + e.getMessage());
            e.printStackTrace();
            return "Error processing sales data request: " + e.getMessage();
        }
    }
    
    /**
     * Calculate actual sales for a store based on sales tracking data
     */
    private int calculateStoreSales(Store store) {
        int totalSales = 0;
        
        try {
            String storeName = store.getStoreName();
            
            // Look for sales data for this store's products
            synchronized (salesData) {
                for (String key : salesData.keySet()) {
                    if (key.startsWith(storeName + ":")) {
                        int[] data = salesData.get(key);
                        totalSales += data[0]; // Add units sold
                    }
                }
            }

            System.out.println("[Worker " + workerId + "] Actual sales for " + 
                              storeName + ": " + totalSales);
            return totalSales;
            
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error calculating store sales: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get a list of visible products for a specific store
     * Format of response: "ProductName,Price,AvailableAmount|ProductName,Price,AvailableAmount|..."
     */
    private String getStoreVisibleProducts(String storeName) {
        try {
            System.out.println("[Worker " + workerId + "] Getting visible products for store: " + storeName);
            
            // Check if store exists in our cache
            if (!storeCache.containsKey(storeName)) {
                System.out.println("[Worker " + workerId + "] Store not found: " + storeName);
                return "Store not found";
            }
            
            Store store = storeCache.get(storeName);
            
            // Build a list of visible products
            StringBuilder productsList = new StringBuilder();
            int visibleProductCount = 0;
            
            // Loop through all products and include only visible ones
            for (Product product : store.getProducts()) {
                // Skip invisible products
                if (!product.getVisible()) {
                    continue;
                }
                
                // Add pipe separator if needed
                if (visibleProductCount > 0) {
                    productsList.append("|");
                }
                
                // Format: ProductName,Price,AvailableAmount
                productsList.append(String.format("%s,%.2f,%d",
                    product.getProductName(),
                    product.getPrice(),
                    product.getAvailableAmount()));
                
                visibleProductCount++;
            }
            
            System.out.println("[Worker " + workerId + "] Found " + visibleProductCount + 
                             " visible products in store: " + storeName);
            
            // Return the formatted list or a no products message
            return visibleProductCount > 0 ? productsList.toString() : "No products available";
            
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error getting store products: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Get sales data for specific products or all products
     * Format of response: "ProductName:SalesCount:TotalIncome|ProductName:SalesCount:TotalIncome|...|Total:TotalCount:TotalIncome"
     */
    private String getSalesByProduct(String command) {
        try {
            // Extract product name filter from command (if any)
            String productFilter = "";
            if (command.startsWith("getSalesByProduct ")) {
                productFilter = command.substring("getSalesByProduct ".length()).trim().toLowerCase();
            }
            
            System.out.println("[Worker " + workerId + "] Getting sales data for product: " + 
                              (productFilter.isEmpty() ? "all products" : productFilter));
            
            StringBuilder results = new StringBuilder();
            int totalSalesCount = 0;
            double totalIncome = 0.0;
            int productCount = 0;
            
            // Map to collect and aggregate sales data by product name
            Map<String, double[]> productSales = new HashMap<>();
            
            // Iterate through the sales data
            for (String key : salesData.keySet()) {
                String[] parts = key.split(":");
                if (parts.length >= 2) {
                    String storeName = parts[0];
                    String productName = parts[1];
                    
                    // Skip if product doesn't match filter
                    if (!productFilter.isEmpty() && !productName.toLowerCase().contains(productFilter)) {
                        continue;
                    }
                    
                    int[] data = salesData.get(key);
                    int unitsSold = data[0];
                    double income = data[1];
                    
                    // Skip if no sales
                    if (unitsSold <= 0) {
                        continue;
                    }
                    
                    // Aggregate sales by product name
                    double[] existing = productSales.getOrDefault(productName, new double[]{0, 0});
                    existing[0] += unitsSold;
                    existing[1] += income;
                    productSales.put(productName, existing);
                    
                    // Add to totals
                    totalSalesCount += unitsSold;
                    totalIncome += income;
                }
            }
            
            // Build the result string
            for (Map.Entry<String, double[]> entry : productSales.entrySet()) {
                if (productCount > 0) {
                    results.append("|");
                }
                
                // Format: ProductName:SalesCount:TotalIncome
                results.append(entry.getKey()).append(":")
                       .append((int)entry.getValue()[0]).append(":")
                       .append(entry.getValue()[1]);
                
                productCount++;
            }
            
            // Add total sales to the results
            if (productCount > 0) {
                results.append("|Total:").append(totalSalesCount).append(":").append(totalIncome);
                return results.toString();
            } else {
                return "No sales data found";
            }
            
        } catch (Exception e) {
            System.err.println("[Worker " + workerId + "] Error getting product sales data: " + e.getMessage());
            e.printStackTrace();
            return "Error processing product sales data request: " + e.getMessage();
        }
    }
}