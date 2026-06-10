package multimedia;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class LoadBalancer {
    private static final List<Integer> servers = Arrays.asList(5000, 5001);
    private static int currentIndex = 0;
    
    public static void main(String[] args) throws IOException{
        int port = 4000;
        
        try(ServerSocket socket = new ServerSocket(port)){
            System.out.println("==================");
            System.out.println("LOAD BALANCER INITIALIZED");
            System.out.println("==================");
            System.out.println("Load Balancer started on port: " + port);
  
            while (true) {
                Socket cSocket = socket.accept(); //accept next incoming client connection
                int targetPort = getNextServerPort(); //round-robin for next backend server

                System.out.println("Incoming client: " + cSocket.getInetAddress());
                System.out.println("Forwarding to server port: " + targetPort);
				
				//handle client in a seperate thread for concurrent connections
                new Thread(() -> handleClient(cSocket, targetPort)).start();
            }
        }catch(IOException e){
            e.printStackTrace();
        }  
    }
    
	//returns the port of the next backend server in round-robin order
    private static synchronized int getNextServerPort(){
        int port = servers.get(currentIndex);
        currentIndex = (currentIndex + 1) % servers.size();
        return port;
    }
    
    private static void handleClient(Socket cSocket, int sPort){
        Socket sSocket = null;
        try{
            sSocket = new Socket("127.0.0.1", sPort); //open connection to the selected backend server
            final Socket finalServerSocket = sSocket;
            
            //Pipe client -> server
            Thread clientToServer = new Thread(() -> forward(cSocket, finalServerSocket));
            //Pipe server -> client
            Thread serverToClient = new Thread(() -> forward(finalServerSocket, cSocket));
            
            clientToServer.start();
            serverToClient.start();
            
            clientToServer.join();
            serverToClient.join();
            
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try{ //close client socket if still open 
                if(cSocket != null && !cSocket.isClosed()) cSocket.close();
            }catch(IOException e){
                e.printStackTrace();
            }
            
            try{ //close server socket if still open
                if(sSocket != null && !sSocket.isClosed()) sSocket.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
            
        }
    }
    
    private static void forward(Socket input, Socket output){
        try{
            InputStream in = input.getInputStream();
            OutputStream out = output.getOutputStream();
            
            byte[] buffer = new byte[4096];
            int bytes;
            
            while((bytes = in.read(buffer)) != -1){
                out.write(buffer, 0, bytes);
                out.flush();
            }
        }catch(IOException e){
            //normal on disconnect
        }
    }
}
