package Manager;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Scanner;
import java.util.List;
import Objects.*;


public class Manager {
    // Use the same MASTER_PORT as defined in Master
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 5055;

    private Socket masterSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public static void main(String[] args) {
        new Manager().start();
    }

    private void connectToMaster() {
        try {
            masterSocket = new Socket(MASTER_HOST, MASTER_PORT);
            out = new ObjectOutputStream(masterSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(masterSocket.getInputStream());
            System.out.println("Connected to Master server at " + MASTER_HOST + ":" + MASTER_PORT);
        } catch (IOException e) {
            System.err.println("Failed to connect to Master server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void disconnectFromMaster() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (masterSocket != null) masterSocket.close();
            System.out.println("Disconnected from Master server");
        } catch (IOException e) {
            System.err.println("Error disconnecting from Master server: " + e.getMessage());
        }
    }

    private void verifyConnection() {
        try {
            if (masterSocket == null || masterSocket.isClosed() || !masterSocket.isConnected()) {
                System.out.println("Connection lost, reconnecting...");
                disconnectFromMaster();
                connectToMaster();
            }
        } catch (Exception e) {
            System.err.println("Error verifying connection: " + e.getMessage());
            connectToMaster();
        }
    }

    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Manager client started.");

            // Establish connection once
            connectToMaster();
            try {
                while (true) {
                    System.out.println("\nAvailable commands:");
                    System.out.println("1. addStore - Add a new store");
                    System.out.println("2. updateStock - Update product stock");
                    System.out.println("3. addProduct - Add a new product to a store");
                    System.out.println("4. removeProduct - Remove a product from a store");
                    System.out.println("5. showSalesByCategory - Show total sales by food category");
                    System.out.println("6. showSalesByProduct - Show total sales and income by product");
                    System.out.println("7. exit - Exit the application");
                    System.out.print("Enter command: ");

                    String input = scanner.nextLine().trim();
                    switch (input) {
                        case "1": addStore(scanner); break;
                        case "2": updateProductStock(scanner); break;
                        case "3": addProduct(scanner); break;
                        case "4": removeProduct(scanner); break;
                        case "5": showSalesByFoodCategory(scanner); break;
                        case "6": showSalesByProduct(scanner); break;
                        case "7": System.out.println("Exiting manager application."); break;
                        default: System.out.println("Unknown command. Please try again.");
                    }
                }
            } finally {
                // Disconnect once
                disconnectFromMaster();
            }
        } catch (Exception e) {
            System.err.println("Error in Manager client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a command to Master and returns the response
     */
    private Object sendCommand(String command, Object payload) {
        int retryCount = 0;
        int maxRetries = 3;
        long retryDelayMs = 5000;

        while (retryCount < maxRetries) {
            try {
                verifyConnection();

                System.out.println("Sending command to Master: " + command);
                out.writeObject(command);
                if (payload != null) {
                    System.out.println("Sending payload object to Master");
                    out.writeObject(payload);
                }
                out.flush();

                System.out.println("Waiting for response from Master...");
                Object response = in.readObject();
                System.out.println("Response received: " + response);
                return response;

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error communicating with Master (attempt " + (retryCount + 1) + "): " + e.getMessage());
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        System.out.println("Reconnecting and retrying in " + retryDelayMs/1000 + " seconds...");
                        disconnectFromMaster();
                        Thread.sleep(retryDelayMs);
                        connectToMaster();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return "Communication error: Failed after " + maxRetries + " attempts";
    }

    private void addStore(Scanner scanner) {
        try {
            System.out.println("\n=== Add Store from JSON File ===");
            System.out.print("Enter path to JSON file: ");
            String jsonPath = scanner.nextLine().trim();

            Path path = Paths.get(jsonPath);
            if (!Files.exists(path)) {
                System.out.println("File not found: " + jsonPath);
                return;
            }

            System.out.println("Adding store from: " + jsonPath);
            Object response = sendCommand("addStore", jsonPath);
            System.out.println("Response: " + response);

        } catch (Exception e) {
            System.out.println("Error adding store: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateProductStock(Scanner scanner) {
        try {
            // Get store name
            System.out.println("\n=== Update Product Stock ===");
            System.out.print("Store Name: ");
            String storeName = scanner.nextLine().trim();

            // Send request to get store details as a Store object
            Store store = getStoreInfo(storeName);
            //Store not found
            if (store == null) {
                System.out.println("Store not found or unable to retrieve store information.");
                return;
            }

            // Retrieve products - Filter to only show visible products
            System.out.println("\nAvailable Products:");
            List<Product> visibleProducts = store.getProducts().stream()
                    .filter(Product::getVisible)
                    .toList();

            // No products found
            if (visibleProducts.isEmpty()) {
                System.out.println("No visible products found for this store.");
                return;
            }
            // Display products list
            for (int i = 0; i < visibleProducts.size(); i++) {
                Product product = visibleProducts.get(i);
                System.out.println((i + 1) + ". " + product.getProductName() +
                        " - Type: " + product.getProductType() +
                        " - Stock: " + product.getAvailableAmount() +
                        " - Price: " + product.getPrice());
            }

            // Select product to update
            System.out.print("\nEnter product number to update: ");
            int productIndex = Integer.parseInt(scanner.nextLine().trim()) - 1;

            if (productIndex < 0 || productIndex >= visibleProducts.size()) {
                System.out.println("Invalid product number.");
                return;
            }

            Product selectedProduct = visibleProducts.get(productIndex);
            // Ensure the product has a reference to its store
            selectedProduct.setStore(store);

            String productName = selectedProduct.getProductName();
            int currentStock = selectedProduct.getAvailableAmount();

            System.out.println("\nSelected: " + productName + " (Current Stock: " + currentStock + ")");
            System.out.println("1. Add stock");
            System.out.println("2. Reduce stock");
            System.out.print("Select operation: ");
            String operation = scanner.nextLine().trim();

            System.out.print("Enter quantity: ");
            int quantity = Integer.parseInt(scanner.nextLine().trim());

            if (quantity <= 0) {
                System.out.println("Quantity must be greater than zero.");
                return;
            }

            boolean operationType;

            if (operation.equals("1")) {
                operationType = true;
            } else if (operation.equals("2")) {
                if (quantity > currentStock) {
                    System.out.println("Cannot reduce more than current stock.");
                    return;
                }
                operationType = false;
            } else {
                System.out.println("Invalid operation.");
                return;
            }

            List<Object> payload = List.of(selectedProduct, operationType, quantity);

            // Send update to master
            Object response = sendCommand("updateStock", payload);

            System.out.println("Response: " + response);

        } catch (NumberFormatException e) {
            System.out.println("Invalid number format. Please try again.");
        } catch (Exception e) {
            System.out.println("Error updating product stock: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addProduct(Scanner scanner) {
        try {
            System.out.println("\n=== Add Product to Store ===");
            System.out.print("Store Name: ");
            String storeName = scanner.nextLine().trim();

            // Get store info
            Store store = getStoreInfo(storeName);
            if (store == null) {
                System.out.println("Store not found. Please create the store first.");
                return;
            }

            System.out.print("Product Name: ");
            String productName = scanner.nextLine().trim();

            // Check if product already exists
            Optional<Product> existingProductOpt = store.findProductByName(productName);

            if (existingProductOpt.isPresent()) {
                Product existingProduct = existingProductOpt.get();

                if (existingProduct.getVisible()) {
                    System.out.println("Product already exists and is visible in the store.");
                    return;
                } else {
                    // Product exists but is not visible - offer to restore it
                    System.out.println("Product exists but is currently hidden.");
                    System.out.print("Do you want to restore this product? (yes/no): ");
                    String restore = scanner.nextLine().trim().toLowerCase();

                    if (restore.equals("yes")) {
                        // Restore the product by making it visible again
                        existingProduct.setVisible(true);
                        existingProduct.setStore(store);

                        // Ask if they want to update stock
                        System.out.println("Current stock: " + existingProduct.getAvailableAmount());
                        System.out.print("Do you want to update the stock? (yes/no): ");
                        String updateStock = scanner.nextLine().trim().toLowerCase();

                        if (updateStock.equals("yes")) {
                            System.out.print("Enter new stock amount: ");
                            int newStock = Integer.parseInt(scanner.nextLine().trim());
                            existingProduct.setAvailableAmount(newStock);
                        }

                        // Send update to master
                        Object response = sendCommand("addProduct", existingProduct);
                        System.out.println("Response: " + response);
                        return;
                    } else {
                        System.out.println("Let's create a new product with the same name instead.");
                    }
                }
            }

            // Get new product details
            System.out.print("Product Type: ");
            String productType = scanner.nextLine().trim();

            System.out.print("Available Amount: ");
            int availableAmount;
            try {
                availableAmount = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount. Please enter a number.");
                return;
            }

            System.out.print("Price (EUR): ");
            double price;
            try {
                price = Double.parseDouble(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid price. Please enter a number.");
                return;
            }

            // Create new product
            Product newProduct = new Product(productName, productType, availableAmount, price);
            newProduct.setStore(store);
            newProduct.setVisible(true);

            // Send to master
            Object response = sendCommand("addProduct", newProduct);
            System.out.println("Response: " + response);

        } catch (Exception e) {
            System.err.println("Error adding product: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeProduct(Scanner scanner) {
        try {
            System.out.println("\n=== Remove Product from Store ===");
            System.out.print("Store Name: ");
            String storeName = scanner.nextLine().trim();

            // Get store info
            Store store = getStoreInfo(storeName);
            if (store == null) {
                System.out.println("Store not found or unable to retrieve store information.");
                return;
            }

            // Retrieve visible products only
            System.out.println("\nAvailable Products:");
            List<Product> visibleProducts = store.getProducts().stream()
                    .filter(Product::getVisible)
                    .toList();

            if (visibleProducts.isEmpty()) {
                System.out.println("No visible products found for this store.");
                return;
            }

            // Display products list
            for (int i = 0; i < visibleProducts.size(); i++) {
                Product product = visibleProducts.get(i);
                System.out.printf("%d. %s - Type: %s - Stock: %d - Price: %.2f\n",
                        (i + 1),
                        product.getProductName(),
                        product.getProductType(),
                        product.getAvailableAmount(),
                        product.getPrice()
                );
            }

            // Select product to remove
            System.out.print("\nEnter product number to remove: ");
            int productIndex = Integer.parseInt(scanner.nextLine().trim()) - 1;

            if (productIndex < 0 || productIndex >= visibleProducts.size()) {
                System.out.println("Invalid product number.");
                return;
            }

            Product selectedProduct = visibleProducts.get(productIndex);
            // Ensure the product has a reference to its store
            selectedProduct.setStore(store);
            // Set visibility to false instead of physically removing
            selectedProduct.setVisible(false);

            String productName = selectedProduct.getProductName();

            System.out.println("\nYou are about to hide: " + productName);
            System.out.print("Are you sure? (yes/no): ");
            String confirmation = scanner.nextLine().trim().toLowerCase();

            if (!confirmation.equals("yes")) {
                System.out.println("Operation cancelled.");
                return;
            }

            // Send command to remove product
            Object response = sendCommand("removeProduct", selectedProduct);
            System.out.println("Response: " + response);

        } catch (NumberFormatException e) {
            System.out.println("Invalid number format. Please try again.");
        } catch (Exception e) {
            System.out.println("Error removing product: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private Store getStoreInfo(String storeName) {
        try {
            Object response = sendCommand("getStoreInfo", storeName); // Send storeName as payload
            if (response instanceof Store) {
                return (Store) response;
            }
            System.out.println("Failed to get store info: " + response);
            return null;
        } catch (Exception e) {
            System.out.println("Error getting store info: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Display total sales by food category
     */
    private void showSalesByFoodCategory(Scanner scanner) {
        try {
            System.out.println("\n=== Show Sales by Food Category ===");
            System.out.print("Enter food category (e.g., pizza, fast food, cafe, etc.): ");
            String foodCategory = scanner.nextLine().trim().toLowerCase();
            
            if (foodCategory.isEmpty()) {
                System.out.println("Food category cannot be empty.");
                return;
            }
            
            System.out.println("Fetching sales data for food category: " + foodCategory);
            
            // Send request to get sales data by category
            String salesData = getSalesByFoodCategory(foodCategory);
            
            if (salesData == null || salesData.isEmpty() || salesData.equals("No sales data found")) {
                System.out.println("No sales data found for food category: " + foodCategory);
                return;
            }
            
            // Parse and display the sales data
            parseSalesData(salesData);
            
        } catch (Exception e) {
            System.err.println("Error retrieving sales data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send request to Master to get sales data for a specific food category
     */
    private String getSalesByFoodCategory(String foodCategory) {
        Object response = sendCommand("getSalesByCategory"+foodCategory,null);
        if (response instanceof String) {
            return (String) response;
        }
        return "No sales data found";
    }

    /**
     * Parse and display sales data
     * Expected format: "StoreName:SalesCount|StoreName:SalesCount|...|Total:TotalCount"
     */
    private void parseSalesData(String salesData) {
        try {
            System.out.println("\nSales by Store:");
            System.out.println("--------------------");
            
            String[] parts = salesData.split("\\|");
            int totalSales = 0;
            
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith("Total:")) {
                    // This is the total, handle separately
                    totalSales = Integer.parseInt(part.substring("Total:".length()));
                } else if (part.contains(":")) {
                    // This is a store entry
                    String[] storeData = part.split(":");
                    String storeName = storeData[0];
                    int sales = Integer.parseInt(storeData[1]);
                    
                    System.out.printf("\"%s\": %d,\n", storeName, sales);
                }
            }
            
            System.out.println("\"total\": " + totalSales);
            
        } catch (Exception e) {
            System.err.println("Error parsing sales data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Display total sales and income by product
     */
    private void showSalesByProduct(Scanner scanner) {
        try {
            System.out.println("\n=== Show Sales and Income by Product ===");
            System.out.print("Enter product name (or leave empty to see all products): ");
            String productName = scanner.nextLine().trim();
            
            System.out.println("Fetching sales data for product: " + 
                               (productName.isEmpty() ? "all products" : productName));
            
            // Send request to get sales data by product
            String salesData = getSalesByProduct(productName);
            
            if (salesData == null || salesData.isEmpty() || salesData.equals("No sales data found")) {
                System.out.println("No sales data found for product: " + 
                                  (productName.isEmpty() ? "any product" : productName));
                return;
            }
            
            // Parse and display the sales data
            parseProductSalesData(salesData);
            
        } catch (Exception e) {
            System.err.println("Error retrieving product sales data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send request to Master to get sales data for a specific product
     */
    private String getSalesByProduct(String productName) {
        Object response = sendCommand("getSalesByProduct " + productName, null);
        if (response instanceof String) {
            return (String) response;
        }
        return "No sales data found";
    }

    /**
     * Parse and display product sales data
     * Expected format: "ProductName:SalesCount:TotalIncome|ProductName:SalesCount:TotalIncome|...|Total:TotalCount:TotalIncome"
     */
    private void parseProductSalesData(String salesData) {
        try {
            System.out.println("\nSales and Income by Product:");
            System.out.println("-------------------------------");
            
            String[] parts = salesData.split("\\|");
            int totalSales = 0;
            double totalIncome = 0.0;
            
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith("Total:")) {
                    // This is the total, handle separately
                    String[] totalData = part.split(":");
                    totalSales = Integer.parseInt(totalData[1]);
                    totalIncome = Double.parseDouble(totalData[2]);
                } else if (part.contains(":")) {
                    // This is a product entry
                    String[] productData = part.split(":");
                    String productName = productData[0];
                    int sales = Integer.parseInt(productData[1]);
                    double income = Double.parseDouble(productData[2]);
                    
                    System.out.printf("\"%s\": %d units sold, EUR%.2f income\n", 
                                     productName, sales, income);
                }
            }
            
            System.out.println("\nSummary:");
            System.out.printf("Total units sold: %d\n", totalSales);
            System.out.printf("Total income: EUR%.2f\n", totalIncome);
            
        } catch (Exception e) {
            System.err.println("Error parsing product sales data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

