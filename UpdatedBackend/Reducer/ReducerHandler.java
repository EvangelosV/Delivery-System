package Reducer;

import java.io.*;
import java.net.*;
import java.util.*;

class ReducerHandler extends Thread {
    private Socket workerSocket; // Sockets for the workers to connect to
    private static ObjectOutputStream out; // for workers
    private static ObjectInputStream in;
    private static ObjectOutputStream masterOut;
    private static ObjectInputStream masterIn;

    // Get the shared aggregatedResults map from the Reducer
    private static final Map<String,Object> aggregatedResults = Reducer.getAggregatedResults();

    public ReducerHandler(Socket workerSocket, Socket masterSocket) throws IOException {
        this.workerSocket = workerSocket;

        this.out = new ObjectOutputStream(workerSocket.getOutputStream());
        this.in = new ObjectInputStream(workerSocket.getInputStream());

        this.masterOut = new ObjectOutputStream(masterSocket.getOutputStream());
        masterOut.writeObject("Reducer established connection with Master");
        masterOut.flush();
        this.masterIn = new ObjectInputStream(masterSocket.getInputStream());
    }

    @Override
    public void run() {
        try {
            // Read command from worker
            String command = (String) in.readObject();
            System.out.println("Reducer received command: " + command);

            if (command.equals("mapResult")) {
                // Receive map results from worker
                Object data = in.readObject();
                if (data instanceof Map) {
                    processWorkerData(command, data);
                    out.writeObject("acknowledged");
                    out.flush();
                } else {
                    out.writeObject("Invalid data format");
                    out.flush();
                }
            } else {
                out.writeObject("Unknown command");
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("Error handling connection: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                workerSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void processWorkerData(String command, Object data) {
        if ("mapResult".equals(command) && data instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) data;
            System.out.println("Processing map result with " + resultMap.size() + " entries");

            // 3) All mutations inside one synchronized-block + notifyAll
            synchronized (aggregatedResults) {
                // Increment request counts by type
                String requestType = (String) resultMap.get("requestType");
                String requestKey  = "count_" + requestType;
                aggregatedResults.put(
                        requestKey,
                        ((Integer)aggregatedResults.getOrDefault(requestKey, 0)) + 1
                );

                // Track worker activity
                String workerId = (String) resultMap.get("workerId");
                Map<String,Integer> workerStats = getOrCreateWorkerStats(workerId);
                workerStats.put("requests", workerStats.getOrDefault("requests", 0) + 1);

                // Handle purchases
                if ("purchase".equals(requestType)
                        && resultMap.containsKey("storeName")
                        && resultMap.containsKey("productName")
                        && resultMap.containsKey("quantity")) {

                    String storeName   = (String) resultMap.get("storeName");
                    String productName = (String) resultMap.get("productName");
                    int    quantity    = (Integer) resultMap.get("quantity");

                    // Update store-level sales
                    Map<String,Integer> storeSales = getOrCreateStoreSales(storeName);
                    storeSales.put("totalSales",
                            storeSales.getOrDefault("totalSales", 0) + quantity
                    );

                    // Update per-product count
                    String productKey = storeName + "_" + productName;
                    aggregatedResults.put(
                            productKey,
                            ((Integer)aggregatedResults.getOrDefault(productKey, 0)) + quantity
                    );
                }

                // Update last-activity timestamp
                aggregatedResults.put("lastUpdate", System.currentTimeMillis());

                // 4) Wake the reporter thread
                aggregatedResults.notifyAll();
            }

            System.out.println("Updated aggregated results. Current size: "
                    + aggregatedResults.size());
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Integer> getOrCreateWorkerStats(String workerId) {
        String key = "worker_" + workerId;
        if (!aggregatedResults.containsKey(key)) {
            aggregatedResults.put(key, new HashMap<String, Integer>());
        }
        return (Map<String, Integer>) aggregatedResults.get(key);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Integer> getOrCreateStoreSales(String storeName) {
        String key = "store_" + storeName;
        if (!aggregatedResults.containsKey(key)) {
            aggregatedResults.put(key, new HashMap<String, Integer>());
        }
        return (Map<String, Integer>) aggregatedResults.get(key);
    }
}
