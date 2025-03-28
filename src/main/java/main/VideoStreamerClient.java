package main;

import org.bytedeco.javacv.*;

import javax.sound.sampled.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;

public class VideoStreamerClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 9999;

        try (Socket socket = new Socket(host, port);
             DataInputStream inStream = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected to server.");

            // Display window
            CanvasFrame canvasFrame = new CanvasFrame("Video Streamer");
            canvasFrame.setCanvasSize(640, 480);

            // Audio format: 44.1 kHz, 16-bit, stereo, little-endian
            AudioFormat audioFormat = new AudioFormat(
                    44100,
                    16,
                    2,
                    true,
                    false
            );

            // Increase audio buffer for smoother playback
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(audioFormat, 12288);  // Larger buffer = more stable audio
            audioLine.start();

            while (true) {
                try {
                    // Read frame metadata
                    int frameType = inStream.readByte(); // 1=video, 2=audio
                    int frameLength = inStream.readInt();
                    byte[] frameBytes = inStream.readNBytes(frameLength);

                    // Video
                    if (frameType == 1) {
                        Frame frame = convertBytesToFrame(frameBytes);
                        if (frame != null && canvasFrame.isVisible()) {
                            canvasFrame.showImage(frame);
                        }
                    }
                    // Audio
                    else if (frameType == 2) {
                        audioLine.write(frameBytes, 0, frameBytes.length);
                    }

                } catch (EOFException eof) {
                    System.out.println("Stream ended. Closing client.");
                    break;
                }
            }

            // Cleanup
            canvasFrame.dispose();
            audioLine.drain();
            audioLine.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
