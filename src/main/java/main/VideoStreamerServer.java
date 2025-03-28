package main;

import org.bytedeco.javacv.*;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Iterator;

public class VideoStreamerServer {

    public static void main(String[] args) {
        // Path to your video
        String videoFile = "C:/Users/micha/Downloads/sample-5s.mp4";
        // Server port
        int port = 9999;

        // Aggressive settings for less lag:
        final int targetWidth  = 320;   // smaller resolution
        final int targetHeight = 180;
        final double forcedFps = 15.0;  // lower FPS => fewer frames
        final float jpegQuality = 0.2f; // more compression => smaller data

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            Socket socket = serverSocket.accept();
            System.out.println("Client connected.");

            // Output stream to send frames
            DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());

            // Initialize the grabber
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);

            // Force resolution
            grabber.setImageWidth(targetWidth);
            grabber.setImageHeight(targetHeight);

            // Start grabbing
            grabber.start();

            // Force stable 15 FPS
            grabber.setFrameRate(forcedFps);

            // We'll sleep ~66ms each frame
            long frameIntervalMs = (long) (1000 / forcedFps);
            long lastFrameTime = System.currentTimeMillis();

            Frame frame;
            while ((frame = grabber.grabFrame()) != null) {
                byte[] data;
                int frameType;

                // Distinguish video vs. audio
                if (frame.image != null) {
                    data = compressJPEG(frame, jpegQuality);
                    frameType = 1;
                } else if (frame.samples != null) {
                    data = getAudioBytes(frame);
                    frameType = 2;
                } else {
                    continue;
                }

                // Write frame type + data length + data
                outStream.writeByte(frameType);
                outStream.writeInt(data.length);
                outStream.write(data);

                // For video frames, sleep to maintain ~15 FPS
                if (frameType == 1) {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastFrameTime;

                    long sleepMs = frameIntervalMs - elapsed;
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException ignored) {}
                        lastFrameTime = System.currentTimeMillis();
                    } else {
                        // If behind schedule, skip sleeping
                        lastFrameTime = now;
                    }
                }
            }

            // Cleanup
            grabber.stop();
            outStream.close();
            socket.close();
            System.out.println("Video stream finished.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Compress a video Frame to JPEG bytes with custom quality.
     */
    private static byte[] compressJPEG(Frame frame, float quality) throws IOException {
        Java2DFrameConverter converter = new Java2DFrameConverter();
        BufferedImage image = converter.convert(frame);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Find a JPEG writer
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writers found");
        }
        ImageWriter writer = writers.next();

        // Configure JPEG compression
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality); // 0 = min quality, 1 = max

        try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(mcios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    /**
     * Convert 16-bit PCM audio to little-endian bytes.
     */
    private static byte[] getAudioBytes(Frame frame) {
        if (frame.samples == null || frame.samples.length == 0) {
            return new byte[0];
        }
        try {
            ShortBuffer sb = (ShortBuffer) frame.samples[0];
            short[] shortArray = new short[sb.remaining()];
            sb.get(shortArray);

            ByteBuffer bb = ByteBuffer.allocate(shortArray.length * 2);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            for (short s : shortArray) {
                bb.putShort(s);
            }
            return bb.array();
        } catch (Exception e) {
            System.err.println("Audio conversion error: " + e.getMessage());
            return new byte[0];
        }
    }
}
