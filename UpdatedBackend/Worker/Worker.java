package Worker;

import config.configLoader;
import java.io.*;
import java.net.*;
import java.util.*;

public class Worker {
    // Add Reducer connection details
    private static final int REDUCER_PORT = 7003;
    private static final String hostAddress = "localhost";
    // Add Master connection details
    private static final int MASTER_PORT = 5055;
    private Socket reducerSocket;
    private ServerSocket workerSocket;
    private int WORKER_PORT;
    // Add workerId for identification in the Reducer
    private final String workerId;

    public Worker() {
        // Generate a unique worker ID
        this.workerId = "Worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Worker <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        new Worker().startWorker(port);
    }

    public void startWorker(int port) {
        try {
            WORKER_PORT = port;
            try {
                workerSocket = new ServerSocket(WORKER_PORT);
            } catch (IOException e) {
                System.err.println("Error starting Worker: " + e.getMessage());
                e.printStackTrace();
                return;
            }
            System.out.println("\n==================================");
            System.out.println("Worker node " + workerId + " running on port " + WORKER_PORT);
            System.out.println("Waiting for requests from Master...");
            System.out.println("==================================\n");

            // Establish connection with Reducer
            Socket reducerSocket;
            while (true) {
                try {
                    reducerSocket = new Socket(hostAddress, REDUCER_PORT);
                    break;
                } catch (IOException e) {
                    System.out.println("Reducer not ready, retrying in 1s...");
                    Thread.sleep(3000);
                }
            }
            System.out.println("Worker " + workerId + " connected to Reducer at " + hostAddress + ":" + REDUCER_PORT);

            
            while (true) {
                Socket masterSocket = workerSocket.accept();
                System.out.println("\nReceived connection from: " +
                        masterSocket.getInetAddress().getHostAddress() + ":" + masterSocket.getPort());
                new WorkerHandler(masterSocket, reducerSocket, workerId).start();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error starting Worker: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close sockets
            try {
                if (reducerSocket != null && !reducerSocket.isClosed())
                    reducerSocket.close();
                if (workerSocket  != null && !workerSocket.isClosed())
                    workerSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

}

