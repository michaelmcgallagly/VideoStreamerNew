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
        String videoFile = "C:/Users/micha/Downloads/sample-5s.mp4"; // Adjust path
        int port = 9999;

        // Force these settings for smoother streaming:
        final int targetWidth  = 640;
        final int targetHeight = 360;
        final double forcedFps = 30.0;      // 30 frames per second
        final float jpegQuality = 0.3f;     // Lower quality => smaller frames => less lag

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            Socket socket = serverSocket.accept();
            System.out.println("Client connected.");

            DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());

            // Initialize the grabber
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);

            // Set forced resolution
            grabber.setImageWidth(targetWidth);
            grabber.setImageHeight(targetHeight);

            // Start grabbing
            grabber.start();

            // Force a stable 30 FPS, ignoring original video frame rate
            grabber.setFrameRate(forcedFps);

            // We'll sleep ~33ms each frame
            long frameIntervalMs = (long) (1000 / forcedFps);
            long lastFrameTime = System.currentTimeMillis();

            Frame frame;
            while ((frame = grabber.grabFrame()) != null) {
                byte[] data;
                int frameType;

                if (frame.image != null) {
                    // Video
                    data = compressJPEG(frame, jpegQuality);
                    frameType = 1;
                } else if (frame.samples != null) {
                    // Audio
                    data = getAudioBytes(frame);
                    frameType = 2;
                } else {
                    continue;
                }

                // Write frame type + data length + data
                outStream.writeByte(frameType);
                outStream.writeInt(data.length);
                outStream.write(data);

                // For video frames, sleep to maintain ~30 FPS
                if (frameType == 1) {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastFrameTime;

                    long sleepMs = frameIntervalMs - elapsed;
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException ignored) {}
                        lastFrameTime = System.currentTimeMillis(); // update after sleep
                    } else {
                        // If we're behind schedule, skip sleeping
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
     * Compress a video Frame to JPEG bytes with a custom quality.
     */
    private static byte[] compressJPEG(Frame frame, float quality) throws IOException {
        // Convert the Frame to a BufferedImage
        Java2DFrameConverter converter = new Java2DFrameConverter();
        BufferedImage image = converter.convert(frame);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Find JPEG writer
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writers found");
        }
        ImageWriter writer = writers.next();

        // Configure JPEG compression
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality); // 0 = worst, 1 = best

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
    }   //
}