import org.bytedeco.javacv.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class VideoStreamerServer {
    public static void main(String[] args) {
        String videoFile = "C:/Users/andre/Videos/dualipa.mp4";
        int port = 9999;

        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server listening on port " + port);

            Socket socket = serverSocket.accept();
            System.out.println("Client connected.");

            DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());

            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
            grabber.start();

            Frame frame;
            while ((frame = grabber.grabFrame()) != null) { // grabFrame() handles both audio and video
                if (frame.image != null) {
                    try {
                        byte[] frameBytes = getFrameBytes(frame);
                        outStream.writeInt(frameBytes.length);
                        outStream.write(frameBytes);
                        outStream.flush();
                    } catch (IOException e) {
                        System.err.println("Error processing video frame: " + e.getMessage());
                    }
                } else if (frame.samples != null) { // Handle audio frames
                    try {
                        byte[] audioBytes = getAudioBytes(frame);
                        outStream.writeInt(audioBytes.length);
                        outStream.write(audioBytes);
                        outStream.flush();
                    } catch (IOException e) {
                        System.err.println("Error processing audio frame: " + e.getMessage());
                    }
                }
                Thread.sleep(40);
            }

            grabber.stop();
            socket.close();
            serverSocket.close();
            System.out.println("Video stream finished.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] getFrameBytes(Frame frame) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Java2DFrameConverter converter = new Java2DFrameConverter();

        try {
            BufferedImage bufferedImage = converter.convert(frame);
            ImageIO.write(bufferedImage, "jpg", byteArrayOutputStream);
        } catch (Exception e) {
            System.err.println("Failed to convert frame to image: " + e.getMessage());
            throw new IOException("Failed to convert frame to image", e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] getAudioBytes(Frame frame) throws IOException {
        if (frame.samples == null || frame.samples.length == 0) {
            return new byte[0]; // Return empty byte array if no audio samples
        }

        try {
            if (frame.samples[0] instanceof ShortBuffer) {
                ShortBuffer shortBuffer = (ShortBuffer) frame.samples[0];
                short[] shortArray = new short[shortBuffer.remaining()];
                shortBuffer.get(shortArray);

                ByteBuffer byteBuffer = ByteBuffer.allocate(shortArray.length * 2); // 2 bytes per short
                for (short s : shortArray) {
                    byteBuffer.putShort(s);
                }
                return byteBuffer.array();

            } else if (frame.samples[0] instanceof FloatBuffer) {
                FloatBuffer floatBuffer = (FloatBuffer) frame.samples[0];
                float[] floatArray = new float[floatBuffer.remaining()];
                floatBuffer.get(floatArray);

                ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * 4); // 4 bytes per float
                for (float f : floatArray) {
                    byteBuffer.putFloat(f);
                }
                return byteBuffer.array();
            } else {
                System.err.println("Unsupported audio buffer type.");
                return new byte[0]; // Return empty byte array for unsupported types
            }
        } catch (Exception e) {
            System.err.println("Error converting audio buffer: " + e.getMessage());
            return new byte[0]; // return empty byte array in case of error.
        }
    }
}