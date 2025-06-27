// WebcamTest.java
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_videoio.*;
import org.bytedeco.opencv.opencv_highgui.*;

import static org.bytedeco.opencv.global.opencv_highgui.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class WebcamTest {

    public static void main(String[] args) {
        // Load the native OpenCV library manually (optional, but good for debugging)
        // System.loadLibrary(org.bytedeco.opencv.global.opencv_core.NATIVE_LIBRARY_NAME);
        // If you loaded all javacv-platform jars, this might not be strictly needed for basic stuff.

        // Create a new VideoCapture object.
        // 0 usually refers to the default webcam. If you have multiple, try 1, 2, etc.
        VideoCapture camera = new VideoCapture(0);

        // Check if the camera opened successfully
        if (!camera.isOpened()) {
            System.err.println("ERROR: Could not open camera. Check if it's connected and not in use.");
            return;
        }

        // Create a Mat object to store frames
        Mat frame = new Mat();

        // Create a window to display the camera feed
        namedWindow("Webcam Feed", WINDOW_AUTOSIZE);

        System.out.println("Webcam feed started. Press 'q' to quit.");

        while (true) {
            // Read a new frame from the camera
            camera.read(frame);

            // Check if the frame is empty (e.g., camera disconnected)
            if (frame.empty()) {
                System.err.println("ERROR: Received empty frame. Camera might have disconnected.");
                break;
            }

            // Display the frame in the window
            imshow("Webcam Feed", frame);

            // Wait for a key press (1ms delay)
            // If 'q' is pressed, break the loop
            if (waitKey(1) == 'q') {
                break;
            }
        }

        // Release the camera and destroy the window when done
        camera.release();
        destroyAllWindows();
        System.out.println("Webcam feed stopped.");
    }
}