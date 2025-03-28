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

    // Sentinel for stopping decode thread
    private static final byte[] SENTINEL = new byte[0];

    public static void main(String[] args) {
        // Update host/port as needed
        String host = "10.12.31.180";
        int port = 9999;

        // Thread-safe queue for incoming video frames
        BlockingQueue<byte[]> videoQueue = new LinkedBlockingQueue<>();

        try (Socket socket = new Socket(host, port);
             DataInputStream inStream = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected to server.");

            // Display window
            CanvasFrame canvasFrame = new CanvasFrame("Video Streamer");
            canvasFrame.setCanvasSize(640, 480);

            // Setup audio: 44.1kHz, 16-bit, stereo, little-endian
            AudioFormat audioFormat = new AudioFormat(44100, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(audioFormat, 12288);
            audioLine.start();

            // Reader Thread
            Thread readerThread = new Thread(() -> {
                try {
                    while (true) {
                        int frameType;
                        try {
                            frameType = inStream.readByte(); // 1=video, 2=audio
                        } catch (EOFException eof) {
                            System.out.println("Stream ended (reader).");
                            break;
                        }

                        int frameLength = inStream.readInt();
                        byte[] frameBytes = inStream.readNBytes(frameLength);

                        // Video => queue it
                        if (frameType == 1) {
                            videoQueue.offer(frameBytes);
                        }
                        // Audio => play immediately
                        else if (frameType == 2) {
                            audioLine.write(frameBytes, 0, frameBytes.length);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Reader thread error: " + e.getMessage());
                } finally {
                    // Signal decode thread to stop
                    videoQueue.offer(SENTINEL);
                }
            }, "ReaderThread");
            readerThread.start();

            // Decode Thread
            Thread decodeThread = new Thread(() -> {
                try {
                    while (true) {
                        // If backlog > 2, skip older frames
                        while (videoQueue.size() > 2) {
                            videoQueue.poll();
                        }

                        byte[] compressedBytes = videoQueue.take();
                        if (compressedBytes == SENTINEL) {
                            System.out.println("Decode thread got sentinel; stopping.");
                            break;
                        }

                        Frame frame = convertBytesToFrame(compressedBytes);
                        if (frame != null && canvasFrame.isVisible()) {
                            canvasFrame.showImage(frame);
                        }
                    }
                } catch (InterruptedException e) {
                    System.err.println("Decode thread interrupted: " + e.getMessage());
                }
            }, "DecodeThread");
            decodeThread.start();

            // Wait for decode thread to finish
            decodeThread.join();

            // Cleanup
            canvasFrame.dispose();
            audioLine.drain();
            audioLine.close();

            readerThread.join();
            System.out.println("Client finished.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert JPEG bytes to JavaCV Frame.
     */
    private static Frame convertBytesToFrame(byte[] frameBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
            if (image == null) {
                System.err.println("Error decoding image, skipping frame.");
                return null;
            }
            return new Java2DFrameConverter().convert(image);
        } catch (IOException e) {
            System.err.println("Error converting video frame: " + e.getMessage());
            return null;
        }
    }
}
