package multimedia;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class StreamingClient {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    
	//ports for each protocol 
    private int tcpPlaybackPort;
    private int tcpRecordPort;
    private int udpPlaybackPort;
    private int udpRecordPort;
    private int rtpPlaybackPort;
    private int rtpRecordPort;

    public StreamingClient(String host, int port) throws IOException{

        socket = new Socket(host, port);
        
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        out = new PrintWriter(socket.getOutputStream(), true);
    }
    
    public List<String> requestVideos(double speedMbps, String format) throws IOException{
        int maxResolution = determineMaxResolution(speedMbps);
        
		//sending speed, format and max resolution to the server
        out.println(speedMbps);
        out.println(format);
        out.println(maxResolution);
        
        List<String> videos = new ArrayList<>();
        
        String line;

        while((line = in.readLine()) != null){
            if("END".equals(line)) break;
            videos.add(line);
        }
        
		//read ports
        while((line = in.readLine()) != null){
            if("END_PORTS".equals(line)) break;
            
            if(line.startsWith("TCP_PLAYBACK_PORT:")){
                tcpPlaybackPort = Integer.parseInt(line.split(":")[1]);
            }else if(line.startsWith("TCP_RECORD_PORT:")){
                tcpRecordPort = Integer.parseInt(line.split(":")[1]);
            } else if (line.startsWith("UDP_PLAYBACK_PORT:")) {
                udpPlaybackPort = Integer.parseInt(line.split(":")[1]);
            } else if (line.startsWith("UDP_RECORD_PORT:")) {
                udpRecordPort = Integer.parseInt(line.split(":")[1]);
            } else if (line.startsWith("RTP_PLAYBACK_PORT:")) {
                rtpPlaybackPort = Integer.parseInt(line.split(":")[1]);
            } else if (line.startsWith("RTP_RECORD_PORT:")) {
                rtpRecordPort = Integer.parseInt(line.split(":")[1]);
            }
        }

        return videos;
    }
    
    public void selectVideo(String videoName){
        out.println(videoName);
    }
    
    public void sendProtocol(String protocol){
        out.println(protocol);
    }
    
    public void sendRecordFlag(boolean recordEnabled){
        out.println(recordEnabled ? "true" : "false");
    }
    
	//getters
    public int getTcpPlaybackPort() {
        return tcpPlaybackPort;
    }

    public int getTcpRecordPort() {
        return tcpRecordPort;
    }

    public int getUdpPlaybackPort() {
        return udpPlaybackPort;
    }

    public int getUdpRecordPort() {
        return udpRecordPort;
    }

    public int getRtpPlaybackPort() {
        return rtpPlaybackPort;
    }

    public int getRtpRecordPort() {
        return rtpRecordPort;
    }
    
	//maximum supported resolution
    private int determineMaxResolution(double speedMbps){
        if(speedMbps < 1.5) return 240;
        if(speedMbps < 3.0) return 360;
        if(speedMbps < 5.0) return 480;
        if(speedMbps < 7.0) return 720;
        return 1080;
    }
	
	//close input, output stream and socket connection
    public void close(){
        try{
            if(in != null) in.close();
            
            if(out != null){
                out.flush();
                out.close();
            }
            
            if(socket != null && !socket.isClosed()) socket.close();
            
        } catch(IOException e){
            e.printStackTrace();
        }
    }
    
}
