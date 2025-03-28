package main;

import org.bytedeco.javacv.*;

import javax.sound.sampled.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoStreamerClient {

    // We'll use an empty byte array as a sentinel to signal video decoding thread to stop
    private static final byte[] SENTINEL = new byte[0];

    public static void main(String[] args) {
        String host = "10.12.31.180"; // Adjust host/IP as needed
        int port = 9999;

        // A thread-safe queue to store incoming video frames (compressed JPEG)
        BlockingQueue<byte[]> videoQueue = new LinkedBlockingQueue<>();

        try (Socket socket = new Socket(host, port);
             DataInputStream inStream = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected to server.");

            // Prepare the display window
            CanvasFrame canvasFrame = new CanvasFrame("Video Streamer");
            canvasFrame.setCanvasSize(640, 480);

            // Setup audio: 44.1 kHz, 16-bit, stereo, little-endian
            AudioFormat audioFormat = new AudioFormat(44100, 16, 2, true, false);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
            // Larger buffer helps avoid crackling but can increase audio latency
            audioLine.open(audioFormat, 12288);
            audioLine.start();

            // -----------------------
            // Thread 1: Socket Reader
            // -----------------------
            Thread readerThread = new Thread(() -> {
                try {
                    while (true) {
                        // Read frame metadata
                        int frameType;
                        try {
                            frameType = inStream.readByte(); // 1 = video, 2 = audio
                        } catch (EOFException eof) {
                            System.out.println("Stream ended (reader).");
                            break;
                        }

                        int frameLength = inStream.readInt();
                        byte[] frameBytes = inStream.readNBytes(frameLength);

                        // Video -> store in queue
                        if (frameType == 1) {
                            videoQueue.offer(frameBytes);
                        }
                        // Audio -> write directly to audioLine
                        else if (frameType == 2) {
                            audioLine.write(frameBytes, 0, frameBytes.length);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Reader thread error: " + e.getMessage());
                } finally {
                    // Signal decode thread to stop by offering the sentinel
                    videoQueue.offer(SENTINEL);
                }
            }, "ReaderThread");
            readerThread.start();

            // -----------------------------
            // Thread 2: Video Decode/Display
            // -----------------------------
            Thread decodeThread = new Thread(() -> {
                try {
                    while (true) {
                        // Take compressed frame from queue
                        byte[] compressedBytes = videoQueue.take();
                        // If it's the sentinel, end decoding
                        if (compressedBytes == SENTINEL) {
                            System.out.println("Decode thread received sentinel; shutting down.");
                            break;
                        }
                        // Decode the compressed JPEG bytes into a Frame
                        Frame frame = convertBytesToFrame(compressedBytes);
                        // Display if valid
                        if (frame != null && canvasFrame.isVisible()) {
                            canvasFrame.showImage(frame);
                        }
                    }
                } catch (InterruptedException e) {
                    System.err.println("Decode thread interrupted: " + e.getMessage());
                }
            }, "DecodeThread");
            decodeThread.start();

            // -----------------------------------
            // Main thread waits until decode done
            // -----------------------------------
            decodeThread.join();

            // Cleanup
            canvasFrame.dispose();
            audioLine.drain();
            audioLine.close();

            // Also wait for the reader thread to finish if needed
            readerThread.join();

            System.out.println("Client finished.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert compressed JPEG bytes to a JavaCV Frame for display.
     */
    private static Frame convertBytesToFrame(byte[] frameBytes) {
        try {
            // Decode JPEG to BufferedImage
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
            if (image == null) {
                System.err.println("Error decoding image, skipping frame.");
                return null;
            }
            // Convert to JavaCV Frame
            return new Java2DFrameConverter().convert(image);
        } catch (IOException e) {
            System.err.println("Error converting video frame: " + e.getMessage());
            return null;
        }
    }
}
