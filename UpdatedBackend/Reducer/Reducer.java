package Reducer;

import javax.management.ObjectName;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Reducer {
    private static final int REDUCER_PORT = 7003;
    private static final int MASTER_PORT = 5055;
    private static final String MASTER_HOST = "localhost";
    Socket masterSocket;

    private ServerSocket reducerSocket;
    private static Map<String, Object> aggregatedResults = new HashMap<>();

    public Reducer() {
        aggregatedResults.put("systemStartTime", System.currentTimeMillis());
    }

    public static Map<String, Object> getAggregatedResults() {
        return aggregatedResults;
    }

    public static void main(String[] args) {
        new Reducer().startReducer();
    }

    public void startReducer() {
        try (ServerSocket reducerSocket = new ServerSocket(REDUCER_PORT)) {
            System.out.println("Reducer node running on port " + REDUCER_PORT);

            //startMasterCommunicationThread();

            try {
                // Establish connection with Master
                 masterSocket = new Socket(MASTER_HOST, MASTER_PORT);
            } catch (IOException e) {
                System.err.println("Error connecting to Master: " + e.getMessage());
                e.printStackTrace();
            }

            Thread reporter = new Thread(() -> {
                try {
                    synchronized (aggregatedResults) {
                        while (true) {
                            aggregatedResults.wait();
                            ObjectOutputStream masterOut = new ObjectOutputStream(masterSocket.getOutputStream());
                            masterOut.writeObject(new HashMap<>(aggregatedResults));
                            masterOut.flush();
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    System.err.println("Error sending results to Master: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            reporter.setDaemon(true);
            reporter.start();

            while (true) {
                // ACCEPT CONNECTIONS FROM WORKERS
                Socket workerSocket = reducerSocket.accept(); // Accept connection from worker
                new ReducerHandler(workerSocket, masterSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting Reducer: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (reducerSocket != null && !reducerSocket.isClosed()) {
                try {
                    reducerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (masterSocket != null && !masterSocket.isClosed()) {
                try {
                    masterSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
