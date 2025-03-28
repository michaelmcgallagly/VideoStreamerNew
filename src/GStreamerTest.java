import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

public class GStreamerTest {
    public static void main(String[] args) {
        FrameGrabber grabber = new OpenCVFrameGrabber(0); // 0 for the default webcam
        try {
            grabber.start();
            Frame frame;
            CanvasFrame canvas = new CanvasFrame("Webcam", CanvasFrame.getDefaultGamma());
            canvas.setCanvasSize(640, 480);
            while ((frame = grabber.grab()) != null) {
                canvas.showImage(frame);
            }
            grabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
