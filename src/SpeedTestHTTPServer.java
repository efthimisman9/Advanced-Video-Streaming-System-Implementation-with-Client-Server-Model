package multimedia;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;


public class SpeedTestHTTPServer {
    public static void startServer(String folderPath) throws Exception{
        
        HttpServer server = HttpServer.create(new InetSocketAddress(5050), 0);
        
        server.createContext("/speedtest", new HttpHandler() {
           @Override
           public void handle(HttpExchange exchange) throws IOException{
               
               File folder = new File(folderPath);
               File[] files = folder.listFiles();
               
			   //return error if folder is empty or inaccessible
               if(files == null || files.length == 0){
                   String response = "No files found";
                   exchange.sendResponseHeaders(404, response.length());
                   exchange.getResponseBody().write(response.getBytes());
                   exchange.close();
                   return;
               }
               
               //first video
               File video = null;
               for(File f : files){
                   if(f.getName().endsWith(".mp4") ||
                      f.getName().endsWith(".mkv") ||
                      f.getName().endsWith(".avi")){
                            video = f;
                            break;
                   }
               }
               
               if(video == null){
                   String response = "No videos found";
                   exchange.sendResponseHeaders(404, response.length());
                   exchange.getResponseBody().write(response.getBytes());
                   exchange.close();
                   return;
               }
               
               exchange.sendResponseHeaders(200, video.length());
               
               FileInputStream fis = new FileInputStream(video);
               OutputStream os = exchange.getResponseBody();
               
               byte[] buffer = new byte[8192];
               int read;
               
               while((read = fis.read(buffer)) != -1) os.write(buffer, 0, read);
               
               fis.close();
               os.close();
           }
        });
        
        server.start();
        System.out.println("SpeedTest HTTP Server running on port 5050");
    }
    
    public static void main(String[] args) throws Exception{
        startServer("videos");
    }
}
