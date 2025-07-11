// File: src/Master/Master.java
package Master;

import config.configLoader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
import java.net.InetSocketAddress;
import java.util.Scanner;

public class Master {
    private static final int MASTER_PORT = 5055;
    private static int numNodes;
    private static final String STORES_DIR = "data\\stores";
    private static List<Integer> workerPorts = new ArrayList<>();
    private static List<Socket> workerSockets = new ArrayList<>();
    private static String hostAddress = "localhost";



    public static void main(String[] args) {
        try {

            System.out.println("==================================");
            System.out.println("Enter the number of worker nodes to start:");
            Scanner scanner = new Scanner(System.in);
            numNodes = scanner.nextInt();
            for (int i = 1; i <= numNodes; i++) {
                System.out.println("Enter the worker node's " + i +" port:");
                int workerPort = scanner.nextInt();
                workerPorts.add(workerPort);
            }

            System.out.println("Master attempting connections with " + numNodes + " worker nodes");
            System.out.println("Worker ports available: " + workerPorts);

            new Master().startServer();

        } catch (Exception e) {
            System.err.println("Error starting Master server: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static void connectToWorkers(List<Integer> ports) {
        int i = 1;
        for (int port : ports) {
            try {
                Socket s = new Socket(hostAddress, port);
                workerSockets.add(s);
                System.out.println("Connected to worker on port " + port + " (Worker " + i + ")");
            } catch (IOException e) {
                System.err.println("Could not connect to worker on port " + port + " (Worker " + i + ")");
                e.printStackTrace();
            }
        }
    }

    public void startServer() {
        // serverSocket to accept connections from clients (Manager, Customer, Reducer)
        try (ServerSocket serverSocket = new ServerSocket(MASTER_PORT)) {
            System.out.println("Master server running on port " + MASTER_PORT);
            System.out.println("Waiting for client connections (Manager, Customer, Reducer)...");

            // I need socket to establish connections with workers
            connectToWorkers(workerPorts);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
                new MasterHandler(clientSocket, workerPorts, workerSockets).start();
            }
        } catch (IOException e) {
            System.err.println("Error in Master server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close all worker sockets
            for (Socket socket : workerSockets) {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (IOException e) {
                        System.err.println("Error closing worker socket: " + e.getMessage());
                    }
                }
            }
        }
    }
}