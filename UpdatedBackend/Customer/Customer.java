package Customer;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Customer {
    private static final String MASTER_HOST = "localhost";
    private static final int MASTER_PORT = 5055;
    // Default radius for store search in kilometers
    private static final double DEFAULT_RADIUS = 5.0;
    
    // Customer's current location
    private double customerLatitude;
    private double customerLongitude;

    private Socket masterSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public static void main(String[] args) {
        new Customer().start();
    }

    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Customer client started.");

            // Get customer's location first
            setCustomerLocation(scanner);

            // Establish connection once
            connectToMaster();

            while (true) {
                System.out.println("\nAvailable commands:");
                System.out.println("1. findStores - Find stores near you");
                System.out.println("2. updateLocation - Update your location");
                System.out.println("3. searchProducts - Search for products");
                System.out.println("4. buy - Buy a product");
                System.out.println("5. exit - Exit the application");
                System.out.print("Enter command: ");

                String input = scanner.nextLine().trim();

                if (input.equals("5") || input.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting customer application.");
                    break;
                } else if (input.equals("1") || input.equalsIgnoreCase("findStores")) {
                    verifyConnection();
                    findNearbyStores(scanner);
                } else if (input.equals("2") || input.equalsIgnoreCase("updateLocation")) {
                    setCustomerLocation(scanner);
                } else if (input.equals("3") || input.equalsIgnoreCase("searchProducts")) {
                    verifyConnection();
                    searchProducts(scanner);
                } else if (input.equals("4") || input.equalsIgnoreCase("buy")) {
                    verifyConnection();
                    buyProduct(scanner);
                } else {
                    System.out.println("Unknown command. Please try again.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error in Customer client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnectFromMaster();
        }
    }
    
    private void setCustomerLocation(Scanner scanner) {
        try {
            System.out.println("\n=== Set Your Location ===");
            System.out.print("Enter your latitude: ");
            customerLatitude = Double.parseDouble(scanner.nextLine().trim());
            
            System.out.print("Enter your longitude: ");
            customerLongitude = Double.parseDouble(scanner.nextLine().trim());
            
            System.out.println("Location set to: Lat " + customerLatitude + ", Lon " + customerLongitude);
        } catch (NumberFormatException e) {
            System.out.println("Invalid coordinates. Using default location.");
            // Default location (example - roughly Athens, Greece coordinates)
            customerLatitude = 37.9838;
            customerLongitude = 23.7275;
            System.out.println("Location set to default: Lat " + customerLatitude + ", Lon " + customerLongitude);
        }
    }
    
    private void findNearbyStores(Scanner scanner) {
        System.out.println("\n=== Finding Stores Near Your Location ===");
        
        try {
            // Filter type variables
            String filterType = "none";
            String foodCategoryFilter = "";
            int minStarFilter = 0;
            int maxPriceRatingFilter = 3;
            
            // Ask about filtering - using the existing Scanner from the start() method
            System.out.println("Do you want to apply a filter? (y/n)");
            String applyFilter = scanner.nextLine().trim().toLowerCase();
            
            if (applyFilter.equals("y") || applyFilter.equals("yes")) {
                System.out.println("\nSelect filter type:");
                System.out.println("1. Food Category");
                System.out.println("2. Star Rating");
                System.out.println("3. Price Range");
                System.out.print("Enter your choice (1-3): ");
                
                String filterChoice = scanner.nextLine().trim();
                
                switch (filterChoice) {
                    case "1":
                        filterType = "category";
                        System.out.println("\nFilter by food category:");
                        System.out.println("Examples: pizzeria, fast food, cafe, bakery, ice cream, souvlaki");
                        System.out.print("Enter food category: ");
                        foodCategoryFilter = scanner.nextLine().trim().toLowerCase();
                        break;
                        
                    case "2":
                        filterType = "stars";
                        System.out.println("\nFilter by minimum star rating (1-5):");
                        System.out.print("Enter minimum stars: ");
                        try {
                            minStarFilter = Integer.parseInt(scanner.nextLine().trim());
                            if (minStarFilter < 1 || minStarFilter > 5) {
                                System.out.println("Invalid star rating. Using default (no minimum).");
                                minStarFilter = 0;
                                filterType = "none";
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Using no star filter.");
                            filterType = "none";
                        }
                        break;
                        
                    case "3":
                        filterType = "price";
                        System.out.println("\nFilter by maximum price rating:");
                        System.out.println("1 - $ (Budget, avg up to 5 EUR)");
                        System.out.println("2 - $$ (Mid-range, avg up to 15 EUR)");
                        System.out.println("3 - $$$ (Premium, avg over 15 EUR)");
                        System.out.print("Enter maximum price rating (1-3): ");
                        try {
                            maxPriceRatingFilter = Integer.parseInt(scanner.nextLine().trim());
                            if (maxPriceRatingFilter < 1 || maxPriceRatingFilter > 3) {
                                System.out.println("Invalid price rating. Using no price filter.");
                                maxPriceRatingFilter = 3;
                                filterType = "none";
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Using no price filter.");
                            filterType = "none";
                        }
                        break;
                        
                    default:
                        System.out.println("Invalid choice. No filter will be applied.");
                        filterType = "none";
                        break;
                }
            }

            // Format: findStores|latitude|longitude|radius|filterType|filterValue1|filterValue2
            String searchCommand = String.format("findStores|%f|%f|%f|%s|%s|%d|%d",
                                                customerLatitude, customerLongitude, DEFAULT_RADIUS,
                                                filterType, foodCategoryFilter, minStarFilter, maxPriceRatingFilter);

            out.writeObject(searchCommand);
            out.flush();

            Object response = in.readObject();

            if (response instanceof String) {
                String results = (String) response;

                if (results.equals("No stores found")) {
                    System.out.println("No stores found matching your criteria within " +
                                      DEFAULT_RADIUS + "km of your location.");
                } else {
                    System.out.println("\nStores matching your criteria:");
                    String[] stores = results.split("\\|");

                    for (int i = 0; i < stores.length; i++) {
                        String[] storeInfo = stores[i].split(",");
                        if (storeInfo.length >= 6) {
                            String name = storeInfo[0];
                            String category = storeInfo[1];
                            double distance = Double.parseDouble(storeInfo[2]);
                            int stars = Integer.parseInt(storeInfo[3]);
                            double avgPrice = Double.parseDouble(storeInfo[4]);
                            String priceRating = storeInfo[5];

                            // Use text description of stars instead of symbols
                            String starDisplay = stars + " stars";

                            // Format output with plain text and EUR instead of € symbol
                            System.out.printf("%d. %s - %s - %s - %s (Avg price: %.2f EUR) - %.2f km away\n",
                                             (i+1), name, category, starDisplay,
                                             priceRating, avgPrice, distance);
                        }
                    }
                }
            } else {
                System.out.println("Unexpected response from server.");
            }
        } catch (Exception e) {
            System.err.println("Error finding nearby stores: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private void searchProducts(Scanner scanner) {
        System.out.println("\n=== Search Products ===");
        System.out.print("Enter search term (or leave empty to search all products): ");
        String searchTerm = scanner.nextLine().trim();
        try {
            out.writeObject("search " + searchTerm);
            out.flush();
            Object response = in.readObject();
            System.out.println("Response from Master: " + response);
        } catch (Exception e) {
            System.err.println("Error searching products: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void buyProduct(Scanner scanner) {
        System.out.println("\n=== Buy Product ===");
        System.out.print("Enter store name: ");
        String storeName = scanner.nextLine().trim();
        try {
            // Request product list
            out.writeObject("getStoreProducts|" + storeName);
            out.flush();
            Object response = in.readObject();

            if (response instanceof String) {
                String productList = (String) response;
                if (productList.equals("Store not found")) {
                    System.out.println("Store not found: " + storeName);
                    return;
                }
                if (productList.equals("No products available")) {
                    System.out.println("No products available in this store.");
                    return;
                }

                String[] products = productList.split("\\|");
                System.out.println("\nAvailable Products in " + storeName + ":");
                for (int i = 0; i < products.length; i++) {
                    String[] productInfo = products[i].split(",");
                    String productName = productInfo[0];
                    double price = Double.parseDouble(productInfo[1]);
                    int availableAmount = Integer.parseInt(productInfo[2]);
                    System.out.printf("%d. %s - Price: %.2f EUR - Available: %d\n",
                            (i+1), productName, price, availableAmount);
                }

                System.out.print("\nEnter product number to purchase (or 0 to cancel): ");
                int productNumber;
                try {
                    productNumber = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Purchase canceled.");
                    return;
                }
                if (productNumber <= 0 || productNumber > products.length) {
                    System.out.println("Purchase canceled or invalid selection.");
                    return;
                }
                String selectedProduct = products[productNumber-1].split(",")[0];
                System.out.println("Selected product: " + selectedProduct);

                System.out.print("Enter quantity: ");
                int quantity;
                try {
                    quantity = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid quantity. Please enter a number.");
                    return;
                }
                if (quantity <= 0) {
                    System.out.println("Quantity must be greater than zero.");
                    return;
                }

                // Send buy command
                out.writeObject(String.format("buy|%s|%s|%d", storeName, selectedProduct, quantity));
                out.flush();
                Object purchaseResponse = in.readObject();
                System.out.println("Response from Master: " + purchaseResponse);
            } else {
                System.out.println("Unexpected response from server.");
            }
        } catch (Exception e) {
            System.err.println("Error processing purchase: " + e.getMessage());
            e.printStackTrace();
        }
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
                System.out.println("Connection lost. Reconnecting...");
                disconnectFromMaster();
                connectToMaster();
            }
        } catch (Exception e) {
            System.err.println("Error verifying connection: " + e.getMessage());
            connectToMaster();
        }
    }
}