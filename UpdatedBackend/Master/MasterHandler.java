package Master;

import Objects.Product;
import Objects.Store;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Handles individual client connections to the Master server.
 * Each connection is processed in its own thread.
 */
public class MasterHandler extends Thread {
    private Socket socket;
    private List<Integer> workerPorts;
    private List<Socket> workerSockets;
    // Update store directory path to match actual location
    private static final String STORES_DIR = "data\\stores";
    private static String hostAddress = "localhost"; // Default host address
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private List<ObjectOutputStream> workerOutputs = new ArrayList<>();
    private List<ObjectInputStream> workerInputs = new ArrayList<>();

    public MasterHandler(Socket socket, List<Integer> workerPorts, List<Socket> workerSockets) throws IOException {
        this.socket = socket; // Socket for Manager, Customer, Reducer
        this.workerPorts = workerPorts; // List of worker ports
        this.workerSockets = workerSockets; // List of worker sockets

        // Initialize input and output streams for the Master server <-> Manager, Customer, Reducer
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());

        for (Socket workerSocket : workerSockets) {
            ObjectOutputStream workerOut = new ObjectOutputStream(workerSocket.getOutputStream());
            ObjectInputStream workerIn = new ObjectInputStream(workerSocket.getInputStream());
            workerOutputs.add(workerOut);
            workerInputs.add(workerIn);
        }

    }


    @Override
    public void run() {
        try {

            String command = (String) in.readObject();
            System.out.println("Master received command: " + command);

            if (command.startsWith("addStore")) {
                try {
                    // Read the JSON file path sent from the Manager
                    String jsonPath = (String) in.readObject();
                    System.out.println("Master received addStore command for path: " + jsonPath);
                    
                    // Use the modified addStore method with the file path that returns status information
                    String[] result = addStore(jsonPath);
                    boolean success = Boolean.parseBoolean(result[0]);
                    boolean storeExists = Boolean.parseBoolean(result[1]);
                    
                    // Send appropriate response back to Manager based on whether store existed
                    String response;
                    if (storeExists) {
                        response = "Store already exists: " + jsonPath + " (Store is already in the system)";
                    } else if (success) {
                        response = "Store added successfully from: " + jsonPath;
                    } else {
                        response = "Failed to add store from: " + jsonPath;
                    }
                    
                    System.out.println("Sending response to Manager: " + response);
                    out.writeObject(response);
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Error processing addStore command: " + e.getMessage());
                    e.printStackTrace();
                    out.writeObject("Error adding store: " + e.getMessage());
                    out.flush();
                }
            }
            else if (command.equals("getStoreInfo")) {
                try {
                    // Read store name from the input stream
                    String storeName = (String) in.readObject();
                    Store store = getStoreInfo(storeName);
                    out.writeObject(store != null ? store : "Store not found");
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Error processing getStoreInfo: " + e.getMessage());
                    e.printStackTrace();
                    out.writeObject("Error: " + e.getMessage());
                    out.flush();
                }
            }
            else if (command.startsWith("getSalesByCategory")) {
                String foodCategory = command.substring("getSalesByCategory ".length()).trim();
                String result = getSalesByFoodCategory(foodCategory);
                out.writeObject(result);
                out.flush();
            }
            else if (command.startsWith("getSalesByProduct")) {
                String productName = command.substring("getSalesByProduct ".length()).trim();
                String result = getSalesByProduct(productName);
                out.writeObject(result);
                out.flush();
            }
            else if (command.startsWith("findStores")) {
                String result = findNearbyStores(command);
                out.writeObject(result);
                out.flush();
            }
            else if (command.startsWith("search")) {
                String searchTerm = command.substring("search ".length()).trim();
                String result = searchProducts(searchTerm);
                out.writeObject(result);
                out.flush();
            }
            else if (command.startsWith("buy")) {
                String result = processPurchase(command);
                out.writeObject(result);
                out.flush();
            }
            else if (command.equals("updateStock")) {
                try {
                    // Read payload as List<Object>
                    @SuppressWarnings("unchecked")
                    List<Object> payload = (List<Object>) in.readObject();
                    System.out.println("Master: Processing updateStock operation");

                    // Forward request to worker using the renamed method
                    boolean success = forwardUpdateStockToWorker(payload);

                    String response = success ? 
                            "Successfully updated product stock" : 
                            "Failed to update product stock";

                    System.out.println("Master: Response for updateStock: " + response);
                    out.writeObject(response);
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Master: Error processing updateStock: " + e.getMessage());
                    e.printStackTrace();
                    out.writeObject("Error: " + e.getMessage());
                    out.flush();
                }
            }
            else if (command.startsWith("addProduct")) {
                try {
                    // Assume product is sent as a follow-up object
                    Product product = (Product) in.readObject();
                    boolean success = addProductToStore(product);
                    out.writeObject(success ? "Product added successfully" : "Failed to add product");
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Error processing addProduct: " + e.getMessage());
                    out.writeObject("Error adding product: " + e.getMessage());
                    out.flush();
                }
            }
            else if (command.startsWith("removeProduct")) {
                try {
                    // Assume product is sent as a follow-up object
                    Product product = (Product) in.readObject();
                    boolean success = removeProductFromStore(product);
                    out.writeObject(success ? "Product removed successfully" : "Failed to remove product");
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Error processing removeProduct: " + e.getMessage());
                    out.writeObject("Error removing product: " + e.getMessage());
                    out.flush();
                }
            }
            else if (command.equals("reducerResults")) {
                // Receive aggregated results from the Reducer
                Object data = in.readObject();
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked") // Suppress the unchecked cast warning
                    Map<String, Object> resultMap = (Map<String, Object>) data;
                    processReducerResults(resultMap);
                    out.writeObject("acknowledged");
                    out.flush();
                } else {
                    out.writeObject("Invalid data format");
                    out.flush();
                }
            }
            else if (command.startsWith("getStoreProducts")) {
                try {
                    // Format: getStoreProducts|storeName
                    String storeName = command.substring("getStoreProducts|".length()).trim();
                    System.out.println("Master received getStoreProducts request for store: " + storeName);
                    
                    // Forward to appropriate worker
                    String result = getStoreVisibleProducts(storeName);
                    out.writeObject(result);
                    out.flush();
                } catch (Exception e) {
                    System.err.println("Error processing getStoreProducts: " + e.getMessage());
                    e.printStackTrace();
                    out.writeObject("Error: " + e.getMessage());
                    out.flush();
                }
            }
            else {
                out.writeObject("Unknown command");
                out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ex) {
                System.err.println("Error closing socket: " + ex.getMessage());
            }
        }
    }

    private String forwardToWorker(Socket workerSocket, String command, Object payload) {
        try {
            System.out.println("Forwarding to worker on port " + workerSocket.getPort() + ": " + command);
            //ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
            //ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream());

            ObjectOutputStream workerOut = getOutput(workerSocket);
            ObjectInputStream workerIn = getInput(workerSocket);

            try {
                // Connect to worker
                //workerSocket = new Socket(hostAddress, workerPort);
                //out = new ObjectOutputStream(workerSocket.getOutputStream());
                //in = new ObjectInputStream(workerSocket.getInputStream());

                // Send command and payload
                workerOut.writeObject(command);
                workerOut.writeObject(payload);
                workerOut.flush();

                // Get the response from worker
                String response = (String) workerIn.readObject();
                System.out.println("Response from worker: " + response);

                // Return the worker's response so it can be sent back to the Manager
                return response;

            } catch (IOException e) {
                System.err.println("Failed to connect to worker on port " + workerSocket.getPort() + ": " + e.getMessage());
                return "Error: Failed to connect to worker node - " + e.getMessage();
            }
        } catch (Exception e) {
            System.err.println("Error in forwardToWorker: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Adds a store from a JSON file path
     * This method is preserved for potential future use in direct file loading
     * @param jsonPath Path to the JSON file containing store data
     * @return String array with [success, storeExists]
     */
    private String[] addStore(String jsonPath) {
        try {
            Path path = Paths.get(jsonPath);
            if (!Files.exists(path)) {
                System.out.println("File not found: " + jsonPath);
                return new String[]{"false", "false"};
            }
            
            // Read the JSON file content and create a Store object
            Store store = new Store();
            store.insertStore(jsonPath);
            String storeName = store.getStoreName();
            
            if (storeName == null || storeName.isEmpty()) {
                System.err.println("Error: Invalid store data - no store name found");
                return new String[]{"false", "false"};
            }

            // Get the worker port for this store
            Socket workerSocket = getWorkerNode(storeName);

            // Check if the store already exists on the worker before adding
            Store existingStore = checkIfStoreExists(storeName, workerSocket);
            if (existingStore != null) {
                System.out.println("Store '" + storeName + "' already exists on worker port " + workerSocket.getPort() + ", skipping add");
                return new String[]{"true", "true"}; // Return true since the store is already in the system
            }

            // Store doesn't exist, proceed with adding it
            String command = "addStore " + storeName;
            System.out.println("Forwarding store to worker on port " + workerSocket.getPort() + ": " + storeName);
            
            // Forward the actual Store object, not just its string representation
            String response = forwardToWorker(workerSocket, command, store);
            
            boolean success = response != null && !response.startsWith("Error");
            if (success) {
                System.out.println("Store '" + storeName + "' added successfully to worker on port " + workerSocket.getPort());
            } else {
                System.err.println("Failed to add store to worker: " + response);
            }
            
            return new String[]{String.valueOf(success), "false"};
        } catch (Exception e) {
            System.err.println("Error adding store: " + e.getMessage());
            e.printStackTrace();
            return new String[]{"false", "false"};
        }
    }

    private Store getStoreInfo(String storeName) {
        try {
            System.out.println("Master: Looking up store info for: " + storeName);

            // Determine which worker node handles this store
            Socket workerSocket = getWorkerNode(storeName);
            System.out.println("Master: Forwarding request to worker on port: " + workerSocket.getPort());

            // Forward the command and store name to the worker
            String command = "getStoreInfo";


            try {
                ObjectOutputStream workerOut = getOutput(workerSocket);
                ObjectInputStream workerIn = getInput(workerSocket);

                // Send command and store name to worker
                workerOut.writeObject(command);
                workerOut.writeObject(storeName);
                workerOut.flush();

                // Read response from worker
                Object response = workerIn.readObject();

                // Check if response is a Store object
                if (response instanceof Store) {
                    System.out.println("Master: Successfully retrieved store info");
                    return (Store) response;
                } else {
                    System.out.println("Master: Worker returned: " + response);
                    return null;
                }
            } finally {
                if (workerSocket != null) {
                    workerSocket.close();
                }
            }
        } catch (Exception e) {
            System.err.println("Master: Error getting store info: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private boolean forwardUpdateStockToWorker(List<Object> payload) {
        try {
            if (payload.size() != 3) {
                System.err.println("Invalid payload size for updateProductStock");
                return false;
            }

            Product selectedProduct = (Product) payload.get(0);
            Boolean isAddOperation = (Boolean) payload.get(1); // true for add, false for reduce
            int quantity = (int) payload.get(2);

            String storeName = selectedProduct.getStore().getStoreName();
            System.out.println("Processing request to update stock for product '" +
                    selectedProduct.getProductName() + "' in store: " + storeName);
            System.out.println("Operation: " + (isAddOperation ? "Add" : "Reduce") +
                    " " + quantity + " to product stock");

            // Determine which worker node handles this store
            Socket workerSocket = getWorkerNode(storeName);
            System.out.println("Forwarding stock update request to worker on port: " + workerSocket.getPort());

            // Create a command to send to the worker
            String command = "updateStock";

            // Forward the command and update data to the worker
            String response = forwardToWorker(workerSocket, command, payload);

            // Check worker response
            boolean success = response != null && !response.startsWith("Error") &&
                    response.contains("successfully updated stock");

            if (success) {
                System.out.println("Stock for product '" + selectedProduct.getProductName() +
                        "' successfully updated in worker's memory for store: " + storeName);
            } else {
                System.err.println("Failed to update product stock in worker's memory: " + response);
            }

            return success;
        } catch (Exception e) {
            System.err.println("Error updating product stock: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean addProductToStore(Product newProduct) {
        try {
            String storeName = newProduct.getStore().getStoreName();
            System.out.println("Processing request to add product '" + 
                               newProduct.getProductName() + "' to store: " + storeName);
            
            // Determine which worker node handles this store
            Socket workerSocket = getWorkerNode(storeName);
            System.out.println("Forwarding product addition request to worker on port: " + workerSocket.getPort());
            
            // Create a command to send to the worker
            String command = "addProduct";
            
            // Forward the command and product data to the worker
            String response = forwardToWorker(workerSocket, command, newProduct);
            
            // Check worker response
            boolean success = response != null && response.contains("success");
            
            if (success) {
                System.out.println("Product '" + newProduct.getProductName() + 
                                  "' successfully added in worker's memory for store: " + storeName);
            } else {
                System.err.println("Failed to add product to worker's memory: " + response);
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("Error adding product to store: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean removeProductFromStore(Product selectedProduct) {
        try {
            String storeName = selectedProduct.getStore().getStoreName();
            System.out.println("Processing request to remove product '" + 
                               selectedProduct.getProductName() + "' from store: " + storeName);
            
            // Determine which worker node handles this store
            Socket workerSocket = getWorkerNode(storeName);
            System.out.println("Forwarding product removal request to worker on port: " + workerSocket.getPort());
            
            // Create a command to send to the worker
            String command = "removeProduct";
            
            // Forward the command and product data to the worker
            String response = forwardToWorker(workerSocket, command, selectedProduct);
            
            // Check worker response
            boolean success = response != null && response.contains("success");
            
            if (success) {
                System.out.println("Product '" + selectedProduct.getProductName() + 
                                  "' successfully marked as removed in worker's memory for store: " + storeName);
            } else {
                System.err.println("Failed to remove product from worker's memory: " + response);
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("Error removing product from store: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String findNearbyStores(String command) {
        try {
            // Parse the search parameters to determine which stores to query
            // findStores|latitude|longitude|radius|filterType|foodCategory|minStars|maxPriceRating
            String[] parts = command.split("\\|");
            if (parts.length < 4) {
                return "Invalid search format";
            }
            
            // Parse the radius for logging and validation purposes
            double radius = Double.parseDouble(parts[3]);
            
            System.out.println("Processing findNearbyStores command with radius=" + radius);

            // Get all store files to determine which workers to query
            Path storesDir = Paths.get(STORES_DIR);
            System.out.println("Looking for stores in directory: " + storesDir.toAbsolutePath());
            
            if (!Files.exists(storesDir)) {
                System.err.println("Stores directory not found: " + storesDir.toAbsolutePath());
                return "No stores found";
            }
            
            // Map to collect and merge results from workers
            StringBuilder mergedResults = new StringBuilder();
            int totalStoresFound = 0;
            Set<Integer> queriedWorkers = new HashSet<>();
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(storesDir, "*.json")) {
                for (Path file : stream) {
                    String storeName = file.getFileName().toString().replace(".json", "");
                    
                    // Determine which worker handles this store
                    Socket workerSocket = getWorkerNode(storeName);
                    int workerPort = workerSocket.getPort();
                    // Only forward to each worker once
                    if (!queriedWorkers.contains(workerPort)) {
                        queriedWorkers.add(workerPort);
                        
                        System.out.println("Forwarding findStores request to worker on port: " + workerSocket.getPort());
                        String workerResponse = forwardToWorker(workerSocket, command, null);
                        
                        // Process worker response - append to results if valid
                        if (workerResponse != null && !workerResponse.equals("No stores found") && 
                            !workerResponse.startsWith("Error")) {
                            
                            if (totalStoresFound > 0) {
                                mergedResults.append("|");
                            }
                            mergedResults.append(workerResponse);
                            
                            // Count stores in response by counting pipe separators + 1
                            int workerStoreCount = 1;
                            for (int i = 0; i < workerResponse.length(); i++) {
                                if (workerResponse.charAt(i) == '|') {
                                    workerStoreCount++;
                                }
                            }
                            
                            totalStoresFound += workerStoreCount;
                            System.out.println("Worker returned " + workerStoreCount + " stores");
                        }
                    }
                }
            }
            
            // Return the aggregated results
            return totalStoresFound > 0 ? mergedResults.toString() : "No stores found";
            
        } catch (Exception e) {
            System.err.println("Error in findNearbyStores: " + e.getMessage());
            e.printStackTrace();
            return "Error processing search";
        }
    }

    private String searchProducts(String searchTerm) {
        try {
            System.out.println("Processing searchProducts command with term: \"" + searchTerm + "\"");
            
            // Set up to collect search results from all workers
            StringBuilder combinedResults = new StringBuilder("Products matching \"" + searchTerm + "\":\n");
            int totalProductsFound = 0;
            
            // Need to query all worker nodes since any worker might have products matching the search
            //for (Integer workerPort : workerPorts) {
            for (Socket workerSocket : workerSockets) {
                System.out.println("Forwarding search request to worker on port: " + workerSocket.getPort());
                
                // Create the search command
                String command = "search " + searchTerm;
                
                // Forward the search command to this worker
                String workerResponse = forwardToWorker(workerSocket, command, null);
                
                // Process and combine the results if valid
                if (workerResponse != null && !workerResponse.startsWith("Error") && 
                    !workerResponse.contains("No products found")) {
                    
                    // Parse worker response for results
                    // Skip the header line and connect the results
                    int headerEndIndex = workerResponse.indexOf('\n') + 1;
                    if (headerEndIndex > 0 && headerEndIndex < workerResponse.length()) {
                        String productListings = workerResponse.substring(headerEndIndex);
                        
                        // If we already have products, maintain proper numbering by renaming them
                        if (totalProductsFound > 0) {
                            // Split the product listings into lines
                            String[] lines = productListings.split("\n");
                            for (String line : lines) {
                                if (!line.trim().isEmpty()) {
                                    // Extract everything after the number
                                    int dotIndex = line.indexOf(".");
                                    if (dotIndex >= 0) {
                                        String productInfo = line.substring(dotIndex + 1).trim();
                                        combinedResults.append(++totalProductsFound).append(". ").append(productInfo).append("\n");
                                    }
                                }
                            }
                        } else {
                            // This is the first worker with results, append directly
                            combinedResults.append(productListings);
                            
                            // Count the number of products in the response
                            String[] lines = productListings.split("\n");
                            totalProductsFound = lines.length;
                            
                            // Remove empty lines from the count
                            for (String line : lines) {
                                if (line.trim().isEmpty()) {
                                    totalProductsFound--;
                                }
                            }
                        }
                    }
                }
            }
            
            // Return the combined results or a "not found" message
            return totalProductsFound > 0 ? 
                   combinedResults.toString() : 
                   "No products found matching \"" + searchTerm + "\".";
            
        } catch (Exception e) {
            System.err.println("Error in searchProducts: " + e.getMessage());
            e.printStackTrace();
            return "Error processing search: " + e.getMessage();
        }
    }

    private String processPurchase(String command) {
        try {
            // Format: buy|storeName|productName|quantity
            String[] parts = command.split("\\|");
            if (parts.length < 4) {
                return "Invalid purchase command format. Expected: buy|storeName|productName|quantity";
            }

            String storeName = parts[1];
            String productName = parts[2];
            int quantity = Integer.parseInt(parts[3]);
            
            // Create a purchase payload
            List<Object> payload = new ArrayList<>();
            payload.add(storeName);
            payload.add(productName);
            payload.add(quantity);
            
            // Determine which worker node handles this store
            Socket workerSocket = getWorkerNode(storeName);
            System.out.println("Forwarding purchase request to worker on port: " + workerSocket.getPort());
            
            // Forward the command and purchase data to the worker
            String response = forwardToWorker(workerSocket, "buy", payload);
            
            // Check worker response
            if (response != null && response.startsWith("Success")) {
                return response;  // Return the success message from the worker
            } else {
                return response != null ? response : "Failed to process purchase";
            }
        } catch (NumberFormatException e) {
            return "Invalid quantity format: " + e.getMessage();
        } catch (Exception e) {
            System.err.println("Error processing purchase: " + e.getMessage());
            e.printStackTrace();
            return "Error processing purchase: " + e.getMessage();
        }
    }

    /**
     * Gets sales data for stores in a particular food category
     * @param foodCategory The food category to filter by
     * @return String with data in format: "StoreName:SalesCount|StoreName:SalesCount|...|Total:TotalCount"
     */
    private String getSalesByFoodCategory(String foodCategory) {
        try {
            System.out.println("Retrieving sales data for food category: " + foodCategory);
            
            // Create command for workers
            String command = "getSalesByCategory " + foodCategory;
            
            // Track results from workers
            StringBuilder results = new StringBuilder();
            int totalSales = 0;
            int totalStores = 0;
            
            // Need to query all worker nodes for this type of command since any worker
            // could be handling stores with the target food category
            //for (Integer workerPort : workerPorts) {
            for (Socket workerSocket : workerSockets) {
                System.out.println("Forwarding request to worker on port: " + workerSocket.getPort());
                
                // Forward the command to the worker
                String workerResponse = forwardToWorker(workerSocket, command, null);
                
                // Process worker response if valid
                if (workerResponse != null && !workerResponse.startsWith("Error") && 
                    !workerResponse.equals("No sales data found for category: " + foodCategory)) {
                    
                    // Parse the response format: "StoreName:Count|StoreName:Count|...|Total:Count"
                    String[] parts = workerResponse.split("\\|");
                    
                    // Extract individual store data and total from the worker
                    int workerTotal = 0;
                    
                    for (String part : parts) {
                        if (part.startsWith("Total:")) {
                            // This is the total from this worker
                            workerTotal = Integer.parseInt(part.substring("Total:".length()));
                        } else if (part.contains(":")) {
                            // This is a store entry
                            if (totalStores > 0) {
                                results.append("|");
                            }
                            results.append(part);
                            totalStores++;
                        }
                    }
                    
                    // Add to global total
                    totalSales += workerTotal;
                }
            }
            
            // Add the final total to results
            if (totalStores > 0) {
                results.append("|Total:").append(totalSales);
                return results.toString();
            } else {
                return "No sales data found for category: " + foodCategory;
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving sales data: " + e.getMessage());
            e.printStackTrace();
            return "Error processing request";
        }
    }

    /**
     * Gets sales data and income for a specific product or all products
     * @param productName The product name to filter by, or empty string for all products
     * @return String with data in format: "ProductName:SalesCount:TotalIncome|ProductName:SalesCount:TotalIncome|...|Total:TotalCount:TotalIncome"
     */
    private String getSalesByProduct(String productName) {
        try {
            System.out.println("Retrieving sales data for product: " + 
                              (productName.isEmpty() ? "all products" : productName));
            
            // Create command for workers
            String command = "getSalesByProduct " + productName;
            
            // Track results from workers
            StringBuilder results = new StringBuilder();
            int totalSales = 0;
            double totalIncome = 0.0;
            int productCount = 0;
            
            // Need to query all worker nodes since any worker might have relevant data
            //for (Integer workerPort : workerPorts) {
            for (Socket workerSocket : workerSockets) {
                System.out.println("Forwarding request to worker on port: " + workerSocket.getPort());
                
                // Forward the command to the worker
                String workerResponse = forwardToWorker(workerSocket, command, null);
                
                // Process worker response if valid
                if (workerResponse != null && !workerResponse.startsWith("Error") && 
                    !workerResponse.equals("No sales data found")) {
                    
                    // Parse the response format: "ProductName:Count:Income|...|Total:Count:Income"
                    String[] parts = workerResponse.split("\\|");
                    
                    // Extract data from the worker response
                    for (String part : parts) {
                        if (part.startsWith("Total:")) {
                            // This is the total from this worker
                            String[] totalData = part.split(":");
                            if (totalData.length >= 3) {
                                totalSales += Integer.parseInt(totalData[1]);
                                totalIncome += Double.parseDouble(totalData[2]);
                            }
                        } else if (part.contains(":")) {
                            // This is a product entry
                            // Check if we already have this product in our results
                            String[] productData = part.split(":");
                            String currentProduct = productData[0];
                            int currentSales = Integer.parseInt(productData[1]);
                            double currentIncome = Double.parseDouble(productData[2]);
                            
                            // Check if this product is already in our results
                            boolean productFound = false;
                            String[] existingProducts = results.toString().split("\\|");
                            StringBuilder updatedResults = new StringBuilder();
                            
                            for (int i = 0; i < existingProducts.length; i++) {
                                if (!existingProducts[i].isEmpty()) {
                                    String[] existingData = existingProducts[i].split(":");
                                    if (existingData[0].equals(currentProduct)) {
                                        // Product already exists, update the counts
                                        int updatedSales = Integer.parseInt(existingData[1]) + currentSales;
                                        double updatedIncome = Double.parseDouble(existingData[2]) + currentIncome;
                                        
                                        if (i > 0) updatedResults.append("|");
                                        updatedResults.append(currentProduct).append(":")
                                                    .append(updatedSales).append(":")
                                                    .append(updatedIncome);
                                        productFound = true;
                                    } else {
                                        // Not the same product, keep it as is
                                        if (i > 0) updatedResults.append("|");
                                        updatedResults.append(existingProducts[i]);
                                    }
                                }
                            }
                            
                            if (!productFound) {
                                // New product, add it to results
                                if (results.length() > 0) results.append("|");
                                results.append(part);
                                productCount++;
                            } else {
                                // Update results with the merged data
                                results = updatedResults;
                            }
                        }
                    }
                }
            }
            
            // Add the grand total to the results
            if (productCount > 0) {
                if (results.length() > 0) results.append("|");
                results.append("Total:").append(totalSales).append(":").append(totalIncome);
                return results.toString();
            } else {
                return "No sales data found";
            }
            
        } catch (Exception e) {
            System.err.println("Error retrieving product sales data: " + e.getMessage());
            e.printStackTrace();
            return "Error processing request";
        }
    }

    /**
     * Process aggregated results received from the Reducer
     * This method handles the data sent periodically from the Reducer
     * which includes statistics about request types, worker nodes,
     * and sales information.
     */
    private void processReducerResults(Map<String, Object> data) {
        try {
            System.out.println("\n=== Received Reducer Results ===");
            
            // Get total requests count
            Integer totalRequests = (Integer) data.getOrDefault("totalRequests", 0);
            System.out.println("Total requests processed: " + totalRequests);
            
            // Process system information
            Long systemStartTime = (Long) data.getOrDefault("systemStartTime", 0L);
            Long reportTime = (Long) data.getOrDefault("reportTime", System.currentTimeMillis());
            long uptimeMinutes = (reportTime - systemStartTime) / (1000 * 60);
            System.out.println("System uptime: " + uptimeMinutes + " minutes");
            
            // Display request statistics by type
            System.out.println("\nRequest Statistics by Type:");
            for (String key : data.keySet()) {
                if (key.startsWith("count_") && data.get(key) instanceof Integer) {
                    String requestType = key.substring("count_".length());
                    Integer count = (Integer) data.get(key);
                    System.out.println("  " + requestType + ": " + count + " requests");
                }
            }
            
            // Display worker statistics
            System.out.println("\nWorker Node Statistics:");
            for (String key : data.keySet()) {
                if (key.startsWith("worker_") && data.get(key) instanceof Map) {
                    String workerId = key.substring("worker_".length());
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> workerStats = (Map<String, Integer>) data.get(key);
                    System.out.println("  Worker " + workerId + ": " + 
                                       workerStats.getOrDefault("requests", 0) + " requests");
                }
            }
            
            // Display store sales statistics
            System.out.println("\nStore Sales Statistics:");
            for (String key : data.keySet()) {
                if (key.startsWith("store_") && data.get(key) instanceof Map) {
                    String storeName = key.substring("store_".length());
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> storeStats = (Map<String, Integer>) data.get(key);
                    System.out.println("  " + storeName + ": " + 
                                      storeStats.getOrDefault("totalSales", 0) + " total sales");
                }
            }
            
            // Log the timestamp of this report
            System.out.println("\nReport timestamp: " + new java.util.Date(reportTime));
            System.out.println("=====================================\n");
            
        } catch (Exception e) {
            System.err.println("Error processing reducer results: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get a list of visible products for a specific store
     * @param storeName Name of the store to get products for
     * @return String with data in format: "ProductName,Price,AvailableAmount|ProductName,Price,AvailableAmount|..."
     */
    private String getStoreVisibleProducts(String storeName) {
        try {
            System.out.println("Master: Getting visible products for store: " + storeName);
            
            // Determine which worker node handles this store
            Socket workerSocket = getWorkerNode(storeName);
            System.out.println("Master: Forwarding request to worker on port: " + workerSocket.getPort());
            
            // Forward the command and store name to the worker
            String command = "getStoreProducts";
            String response = forwardToWorker(workerSocket, command, storeName);
            
            // Return the worker's response
            return response;
            
        } catch (Exception e) {
            System.err.println("Master: Error getting store products: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Checks if a store already exists on the specified worker.
     * @param storeName Name of the store to check.

     * @return Store object if it exists, null otherwise.
     */
    private Store checkIfStoreExists(String storeName, Socket workerSocket) {
        try {
            System.out.println("Checking if store '" + storeName + "' exists on worker port " + workerSocket.getPort());
            String command = "getStoreInfo";

            try {
                ObjectOutputStream workerOut = getOutput(workerSocket);
                ObjectInputStream workerIn = getInput(workerSocket);

                // Send command and store name to worker
                workerOut.writeObject(command);
                workerOut.writeObject(storeName);
                workerOut.flush();

                // Read response from worker
                Object response = workerIn.readObject();

                // Check if response is a Store object
                if (response instanceof Store) {
                    System.out.println("Store '" + storeName + "' exists on worker port " + workerSocket.getPort());
                    return (Store) response;
                } else {
                    System.out.println("Store '" + storeName + "' does not exist on worker port " + workerSocket.getPort());
                    return null;
                }
            } finally {
                if (workerSocket != null) {
                    workerSocket.close();
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking if store exists: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // SUB METHODS TO HELP WITH STREAMS/SOCKETS
    private ObjectOutputStream getOutput(Socket socket) {
        int index = workerSockets.indexOf(socket);
        if (index<0) throw new IllegalArgumentException("Socket not found in workerSockets: " + socket);
        return workerOutputs.get(index);
    }

    private ObjectInputStream getInput(Socket socket) {
        int index = workerSockets.indexOf(socket);
        if (index<0) throw new IllegalArgumentException("Socket not found in workerSockets: " + socket);
        return workerInputs.get(index);
    }

    private Socket getWorkerNode(String storeName) {
        int hash = storeName.hashCode();
        return workerSockets.get(Math.abs(hash) % workerPorts.size());
    }

}