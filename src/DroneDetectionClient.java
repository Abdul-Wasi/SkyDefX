// DroneDetectionClient.java
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DroneDetectionClient {

    private static final String SERVER_IP = "localhost"; // Assuming Python script runs locally
    private static final int SERVER_PORT = 12345; // Port for Python script

    private Consumer<JSONObject> dataConsumer;
    private Socket clientSocket;
    private BufferedReader in;
    private boolean running;
    private ExecutorService executorService;

    public DroneDetectionClient(Consumer<JSONObject> dataConsumer) {
        this.dataConsumer = dataConsumer;
        this.running = false;
        this.executorService = Executors.newSingleThreadExecutor(); // Use a single thread for client operations
    }

    public void startClient() {
        if (running) {
            System.out.println("DroneDetectionClient is already running.");
            return;
        }

        running = true;
        executorService.submit(() -> {
            try {
                System.out.println("DroneDetectionClient: Attempting to connect to Python server at " + SERVER_IP + ":" + SERVER_PORT);
                clientSocket = new Socket(SERVER_IP, SERVER_PORT);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                System.out.println("DroneDetectionClient: Connected to Python detection server.");

                String line;
                while (running && (line = in.readLine()) != null) {
                    try {
                        JSONObject json = new JSONObject(line);
                        // System.out.println("DroneDetectionClient: Received JSON: " + json.toString()); // Uncomment for verbose debug
                        if (dataConsumer != null) {
                            dataConsumer.accept(json);
                        }
                    } catch (org.json.JSONException e) {
                        System.err.println("DroneDetectionClient: Failed to parse JSON: " + line + " - " + e.getMessage());
                    }
                }
            } catch (java.net.ConnectException e) {
                System.err.println("DroneDetectionClient: Connection refused. Is the Python server running on " + SERVER_IP + ":" + SERVER_PORT + "? " + e.getMessage());
            } catch (IOException e) {
                if (running) { // Only print error if client was supposed to be running
                    System.err.println("DroneDetectionClient: Error reading from socket: " + e.getMessage());
                }
            } finally {
                stopClientResources();
            }
        });
    }

    public void stopClient() {
        System.out.println("DroneDetectionClient: Stopping client...");
        running = false;
        executorService.shutdownNow(); // Interrupt any running tasks
        stopClientResources();
        System.out.println("DroneDetectionClient: Client stopped.");
    }

    private void stopClientResources() {
        try {
            if (in != null) in.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("DroneDetectionClient: Error closing resources: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running;
    }
}