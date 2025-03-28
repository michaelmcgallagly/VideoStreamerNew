package main;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class VideoStreamerServer {

    public static void main(String[] args) {
        // Path to your input video
        String videoFile = "C:/Users/micha/Downloads/sample-5s.mp4";
        // TCP port for client
        int port = 9999;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            // Accept a single client
            Socket socket = serverSocket.accept();
            System.out.println("Client connected.");

            // Create a grabber to read the local file
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
            grabber.start();

            // Set up a FrameRecorder that encodes to H.264 in an MPEG-TS container
            OutputStream socketOut = socket.getOutputStream();
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                    socketOut,
                    grabber.getImageWidth(),
                    grabber.getImageHeight()
            );
            recorder.setFormat("mpegts"); // container
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264); // H.264
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);  // AAC

            // Copy original audio info
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());

            // Set a video bitrate and fallback frame rate if none is provided
            recorder.setVideoBitrate(1_000_000); // ~1 Mbps
            double originalFps = grabber.getFrameRate();
            if (originalFps <= 0) {
                originalFps = 30; // fallback
            }
            recorder.setFrameRate(originalFps);

            // Start the recorder
            recorder.start();

            // We'll track real time from now
            long startTime = System.currentTimeMillis();

            Frame frame;
            while ((frame = grabber.grab()) != null) {
                // Use frame.timestamp to pace real-time
                // timestamp is in microseconds (µs)
                long ptsMs = frame.timestamp / 1000; // convert to ms
                long elapsedMs = System.currentTimeMillis() - startTime;
                long waitMs = ptsMs - elapsedMs;
                if (waitMs > 0) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ignored) {}
                }

                // Now record (encode) the frame
                recorder.record(frame);
            }

            // Cleanup
            recorder.stop();
            recorder.release();
            grabber.stop();
            socketOut.close();
            socket.close();

            System.out.println("Streaming finished. Server closed.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
