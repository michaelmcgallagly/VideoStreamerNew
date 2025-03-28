import org.bytedeco.javacv.*;
import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class VideoStreamerClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 9999;

        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connected to server.");

            DataInputStream inStream = new DataInputStream(socket.getInputStream());

            CanvasFrame canvasFrame = new CanvasFrame("Receiving Video");
            canvasFrame.setCanvasSize(640, 480);

            // Audio setup
            AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false); // Adjust parameters as needed
            DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceDataLine.open(audioFormat);
            sourceDataLine.start();

            while (true) {
                int frameLength = inStream.readInt();
                byte[] frameBytes = new byte[frameLength];
                inStream.readFully(frameBytes);

                if (isImage(frameBytes)) {
                    Frame frame = convertBytesToFrame(frameBytes);
                    if (frame != null) {
                        canvasFrame.showImage(frame);
                    }
                } else {
                    playAudio(frameBytes, sourceDataLine);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Frame convertBytesToFrame(byte[] frameBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
            Java2DFrameConverter converter = new Java2DFrameConverter();
            return converter.convert(image);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void playAudio(byte[] audioBytes, SourceDataLine sourceDataLine) {
        sourceDataLine.write(audioBytes, 0, audioBytes.length);
    }

    private static boolean isImage(byte[] frameBytes) {
        try {
            ImageIO.read(new ByteArrayInputStream(frameBytes));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}