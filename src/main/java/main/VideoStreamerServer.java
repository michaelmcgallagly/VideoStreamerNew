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
            // We'll send the encoded stream directly to the socket output
            OutputStream socketOut = socket.getOutputStream();

            // We can pick "mpegts", "flv", or another container that supports H.264
            // Some recommended: "mpegts" (MPEG-TS), "flv", or "matroska".
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(socketOut, grabber.getImageWidth(), grabber.getImageHeight());
            recorder.setFormat("mpegts");         // container
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);  // H.264
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);   // or AV_CODEC_ID_MP2, etc.

            // Copy original sample rate/channels from the grabber if needed
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setAudioChannels(grabber.getAudioChannels());

            // Optionally tune the bitrate or preset for H.264
            recorder.setVideoBitrate(1000_000);   // e.g. 1 Mbps
            recorder.setFrameRate(grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 30);

            // Start the recorder
            recorder.start();

            // Now read frames from grabber, encode them with the recorder
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                // This includes both audio and video frames
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
