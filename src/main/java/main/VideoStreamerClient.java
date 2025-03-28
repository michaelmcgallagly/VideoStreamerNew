package main;

import org.bytedeco.javacv.*;

import java.io.*;
import java.net.Socket;

public class VideoStreamerClient {

    public static void main(String[] args) {
        String host = "localhost"; // or your server's IP
        int port = 9999;

        // We'll store the incoming H.264 stream into a temporary file
        File tempFile = new File("C:/temp/video_stream.ts");  // or use createTempFile

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server. Receiving H.264 stream...");

            // Thread that writes raw data from the socket into tempFile
            Thread writerThread = new Thread(() -> {
                try (InputStream in = socket.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    System.out.println("Socket stream ended.");
                } catch (IOException e) {
                    System.err.println("Writer thread error: " + e.getMessage());
                }
            }, "WriterThread");
            writerThread.start();

            // Meanwhile, let's open the tempFile with FFmpegFrameGrabber after a short delay
            // so the file has some data in it. We'll keep reading as it grows.
            Thread.sleep(2000); // Let some data accumulate

            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tempFile)) {
                grabber.start();

                // Use JavaCV's built-in player or handle audio yourself
                CanvasFrame canvas = new CanvasFrame("H.264 Stream", 1);
                canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);

                Frame frame;
                while ((frame = grabber.grab()) != null) {
                    // If you want to handle audio separately, you'd do so here.
                    if (frame.image != null && canvas.isVisible()) {
                        canvas.showImage(frame);
                    }
                }

                canvas.dispose();
            }

            // Wait for the writer thread to finish
            writerThread.join();
            System.out.println("Client finished.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Potentially delete the temp file or keep it
            // tempFile.delete();
        }
    }
}
