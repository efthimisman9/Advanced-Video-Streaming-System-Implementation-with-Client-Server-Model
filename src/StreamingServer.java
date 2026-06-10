package multimedia;

import java.net.*;
import java.io.*;
import java.util.List;

public class StreamingServer{
    
    public static void main(String[] args) throws Exception{

        int port = 5000; //default port
        
        if(args.length > 0){ //override default port if a valid argument is supplied
            try{
                port = Integer.parseInt(args[0]);
            }catch(NumberFormatException e){
                System.out.println("Invalid port argument. Using default port 5000.");
            }
        }
        
        try(ServerSocket sSocket = new ServerSocket(port)){
            System.out.println("==================");
            System.out.println("SERVER INITIALIZATION COMPLETED");
            System.out.println("==================");
            System.out.println("Server started on port " + port);
            
            if(port == 5000){
				//load all videos
                VideoManager manager = new VideoManager();
                manager.loadVideos("videos");
				
				//generate missing resolution/format variants for video groups
                VideoVariantGenerator generator = new VideoVariantGenerator("videos");
                generator.generateMissingVariants(manager.groupVideoByTitle());
                
				//reload to include new variants
                manager.loadVideos("videos");
            }
            
            while(true){
                Socket cSocket = sSocket.accept();
                System.out.println("New client connected: " + cSocket.getInetAddress());
                new Thread(new ClientHandler(cSocket, port)).start(); //handle each client in seperate thread
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}