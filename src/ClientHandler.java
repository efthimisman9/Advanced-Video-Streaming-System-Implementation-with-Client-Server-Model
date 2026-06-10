package multimedia;

import java.io.*;
import java.net.*;
import java.util.List;

public class ClientHandler implements Runnable {
    
    private final Socket client;
    private final int serverControlPort;
    private static Process activeStreamProcess;
    private static final Object STREAM_LOCK = new Object();

    public ClientHandler(Socket client, int serverControlPort){
        this.client = client;
        this.serverControlPort = serverControlPort;
    }
	
	//returns tcp playback port
    private int tcpPlaybackPort(){
        return serverControlPort == 5000 ? 6000 : 6100;
    }

	//returns tcp recording port
    private int tcpRecordPort(){
        return serverControlPort == 5000 ? 6001 : 6101;
    }

	//returns udp playback port
    private int udpPlaybackPort(){
        return serverControlPort == 5000 ? 7001 : 7101;
    }

	//returns udp recording port
    private int udpRecordPort(){
        return serverControlPort == 5000 ? 7002 : 7102;
    }

	//returns rtp playback port
    private int rtpPlaybackPort(){
        return serverControlPort == 5000 ? 8002 : 8102;
    }

	//returns rtp recording port
    private int rtpRecordPort(){
        return serverControlPort == 5000 ? 8003 : 8103;
    }

    @Override
    public void run(){
        BufferedReader in = null;
        PrintWriter out = null;
        String selectedVideo = null;

        try{

            VideoManager manager = new VideoManager();
            manager.loadVideos("videos");

            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            out = new PrintWriter(client.getOutputStream(), true);

            //1. Receive speed and format
            String speed = in.readLine();
            if(speed == null) return;

            String format = in.readLine();
            if(format == null) return;

            String maxRes = in.readLine();
            if(maxRes == null) return;

            double downloadSpeed = Double.parseDouble(speed);
            String requestedMaxResolution = maxRes;

            //capping for localhost
            if(downloadSpeed > 10) downloadSpeed = 5.0;

            System.out.println("Download speed received: " + downloadSpeed + "Mbps");
            System.out.println("Format received: " + format);
            System.out.println("Max allowed resolution: " + requestedMaxResolution);

            //2. Compute max allowed resolution based on bitrate table
            //String maxResolution = getMaxResolutionForSpeed(downloadSpeed);
            //System.out.println("Max allowed resolution: " + maxResolution);
            //3. Send only suitable videos
            List<VideoFile> videos = manager.getVideosByFormatAndMaxResolution(format, requestedMaxResolution);

            //send video list
            for(VideoFile v : videos) out.println(v.toString());

            out.println("END");

            sendMediaPorts(out);

            //4. Receive selected video
            selectedVideo = in.readLine();
            if(selectedVideo == null){
                System.out.println("Client requested only video list. Closing session.");
                return;
            }
            System.out.println("Client selected: " + selectedVideo);

            //5. Receive protocol
            String protocol = in.readLine();
            if (protocol == null) return;
            System.out.println("Protocol selected: " + protocol);

            //6. Receive record flag
            String recordFlag = in.readLine();
            if(recordFlag == null) return;

            boolean recordEnabled = Boolean.parseBoolean(recordFlag);
            System.out.println("Record enabled: " + recordEnabled);

            VideoFile chosenVideo = manager.findVideoByFileName(selectedVideo);

            if(chosenVideo == null){
                System.out.println("Selected video not found: " + selectedVideo);
                return;
            }

            //start streaming
            synchronized(STREAM_LOCK){
                if(activeStreamProcess != null && activeStreamProcess.isAlive()){
                    activeStreamProcess.destroy();
                    try{
                        activeStreamProcess.waitFor();
                    }catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                    }
                }
                activeStreamProcess = startStreaming(chosenVideo.getPath(), chosenVideo.getResolution(), protocol, recordEnabled);
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally{
            try{

                if (out != null) out.close();

                if (in != null) in.close();

                if (client != null && !client.isClosed()) client.close();

                if (selectedVideo != null) System.out.println("Client disconnected (stream session)");

            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
    
	//sends all protocol-specific ports to the client
    private void sendMediaPorts(PrintWriter out){
        out.println("TCP_PLAYBACK_PORT:" + tcpPlaybackPort());
        out.println("TCP_RECORD_PORT:" + tcpRecordPort());
        out.println("UDP_PLAYBACK_PORT:" + udpPlaybackPort());
        out.println("UDP_RECORD_PORT:" + udpRecordPort());
        out.println("RTP_PLAYBACK_PORT:" + rtpPlaybackPort());
        out.println("RTP_RECORD_PORT:" + rtpRecordPort());
        out.println("END_PORTS");
    }
        
    
	//builds and launched an FFmpeg process to stream a video file
    private Process startStreaming(String videoPath, String resolution, String protocol, boolean recordEnabled) throws IOException {

        String scale = SupportedMedia.getScale(resolution);
        ProcessBuilder pb;
        
        if("TCP".equalsIgnoreCase(protocol)){
            if(recordEnabled){ //stream to both playback and record using the tee muxer
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-re",
                        "-i", videoPath,
                        "-vf", "scale=" + scale,
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-c:a", "aac",
                        "-f", "tee",
                        "[f=mpegts]tcp://127.0.0.1:" + tcpPlaybackPort() + "?listen=1|[f=mpegts]tcp://127.0.0.1:" + tcpRecordPort() + "?listen=1"
                );
            }else{ //stream only to playback port
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-re",
                        "-i", videoPath,
                        "-vf", "scale=" + scale,
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-c:a", "aac",
                        "-f", "mpegts",
                        "tcp://127.0.0.1:" + tcpPlaybackPort() + "?listen=1"
                );
            }
        }else if("UDP".equalsIgnoreCase(protocol)){
            if(recordEnabled){ //stream to both playback and record using the tee muxer
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-re",
                        "-i", videoPath,
                        "-vf", "scale=" + scale,
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-tune", "zerolatency",
                        "-x264-params", "keyint=24:min-keyint=24:scenecut=0:repeat-headers=1",
                        "-c:a", "aac",
                        "-f", "tee",
                        "[f=mpegts]udp://127.0.0.1:" + udpPlaybackPort() + "?pkt_size=1316&buffer_size=65535|[f=mpegts]udp://127.0.0.1:" + udpRecordPort() + "?pkt_size=1316&buffer_size=65535"
                );
            }else{ //stream only to playback port
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-re",
                        "-i", videoPath,
                        "-vf", "scale=" + scale,
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-tune", "zerolatency",
                        "-x264-params", "keyint=24:min-keyint=24:scenecut=0:repeat-headers=1",
                        "-c:a", "aac",
                        "-f", "mpegts",
                        "udp://127.0.0.1:" + udpPlaybackPort() + "?pkt_size=1316&buffer_size=65535"
                );
            }
        }else{ // RTP/UDP
            if(recordEnabled){ //stream to both playback and record using the tee muxer
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-re",
                        "-i", videoPath,
                        "-vf", "scale=" + scale,
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-tune", "zerolatency",
                        "-x264-params", "keyint=24:min-keyint=24:scenecut=0:repeat-headers=1",
                        "-c:a", "aac",
                        "-f", "tee",
                        "[f=mpegts]udp://127.0.0.1:" + rtpPlaybackPort() + "?pkt_size=1316&buffer_size=65535|[f=mpegts]udp://127.0.0.1:" + rtpRecordPort() + "?pkt_size=1316&buffer_size=65535"
                );
            }else{ //stream only to playback port
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-re",
                        "-i", videoPath,
                        "-vf", "scale=" + scale,
                        "-map", "0:v:0",
                        "-map", "0:a?",
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-tune", "zerolatency",
                        "-x264-params", "keyint=24:min-keyint=24:scenecut=0:repeat-headers=1",
                        "-c:a", "aac",
                        "-f", "mpegts",
                        "udp://127.0.0.1:" + rtpPlaybackPort() + "?pkt_size=1316&buffer_size=65535"
                );
            }
        }
        
        System.out.println("Streaming file: " + videoPath);
        System.out.println("Resolution : " + resolution);                
        System.out.println("Protocol: " + protocol);
        System.out.println("Record enabled : " + recordEnabled);

         pb.inheritIO();
        return pb.start();
    }  
}

