package multimedia;

import java.io.File;
import java.io.IOException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;

import java.util.List;
import java.util.concurrent.TimeUnit;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;

import multimedia.SupportedMedia;

public class ClientGUI extends Application {

    private StreamingClient client;
    private double measuredSpeedMbps;
    private CheckBox recordBox;
    private CheckBox adaptiveBox;
    
    private Process currentPlayerProcess;
    private Process currentRecorderProcess;
    
    //adaptive streaming variables
    private volatile boolean adaptiveEnabled = false;
    private volatile boolean playbackActive = false;
    private volatile boolean switchProgress = false;
    
    private String currentSelectedVideo;
    private String currentProtocol;
    private String currentFormat;
    private boolean currentRecordEnabled;
    private String currentRes;
    private Thread adaptiveThread;
    

    @Override
    public void start(Stage stage) throws Exception{
        
        client = null;
        
        SpeedTestService speedTestService = new SpeedTestService();
        measuredSpeedMbps = speedTestService.downloadSpeedMbps();
        
        Label speedLabel = new Label("Download speed: " + String.format("%.2f", measuredSpeedMbps) + "Mbps");
        
        //making of the choices
        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("mp4", "mkv", "avi");
        formatBox.setPromptText("Format");
        
        ComboBox<String> protocolBox = new ComboBox<>();
        protocolBox.getItems().addAll("TCP", "UDP", "RTP/UDP");
        protocolBox.setPromptText("Protocol");
        
        recordBox = new CheckBox("Record Stream");
        recordBox.setSelected(false);
        
        //video loading
        Button loadVideos = new Button("Load Videos");
        ListView<String> videoList = new ListView<>();
        
        //adaptive checkbox
        adaptiveBox = new CheckBox("Enable Adaptive Streaming");
        adaptiveBox.setSelected(false);
        
        //play and exit button
        Button play = new Button("Play");
        Button exit = new Button("Exit");
        
        TextArea bitrateTable = createBitrateTable();
        VBox.setVgrow(bitrateTable, Priority.ALWAYS);
        bitrateTable.setMaxWidth(Double.MAX_VALUE);
        bitrateTable.setPrefHeight(450);
        
        //use of the buttons
        //1.
        loadVideos.setOnAction(e -> {
            try{
                String format = formatBox.getValue();
                
                if(format == null){
                    showAlert("Selection Error",  "Please select format.");
                    return;
                }
                
                reconnectClient();
                
                List<String> videos = client.requestVideos(measuredSpeedMbps, format);

                videoList.getItems().clear();
                videoList.getItems().addAll(videos);
                
                if(videos.isEmpty()) showAlert("No videos ", "No videos found for format " + format + ".");
                
            }catch (Exception ex){
                ex.printStackTrace();
                showAlert("Load Error", "Failed to load videos.");
            }
        });
        
        //2.
        play.setOnAction(e -> {
            String selected = videoList.getSelectionModel().getSelectedItem();
           
            if(selected == null){
                showAlert("Selection Error", "Please select a video first.");
                return;
            }
            
            String protocol = protocolBox.getValue();
            
            if(protocol == null) protocol = chooseDefaultProtocol(selected);
            
            boolean recordEnabled = recordBox.isSelected();
            boolean adaptiveSelected = adaptiveBox.isSelected();
  
            try{
                
                if(client == null){
                    showAlert("Load Error", "Please load videos first.");
                    return;
                }
                
                if (currentPlayerProcess != null && currentPlayerProcess.isAlive()) {
                    currentPlayerProcess.destroy();
                    currentPlayerProcess.waitFor();
                    Thread.sleep(1000);
                }
                
                if (currentRecorderProcess != null && currentRecorderProcess.isAlive()) {
                    currentRecorderProcess.destroy();
                    currentRecorderProcess.waitFor();
                    Thread.sleep(1000);
                }
                
                adaptiveEnabled = false;
                playbackActive = false;
                switchProgress = false;
                if(adaptiveThread != null && adaptiveThread.isAlive()) adaptiveThread.interrupt();
                
                client.selectVideo(selected);

                //starting UDP/ RTP-UDP player to receive packets first
                if("UDP".equalsIgnoreCase(protocol) || "RTP/UDP".equalsIgnoreCase(protocol)){
                    
                    if(recordEnabled){
                        currentRecorderProcess = startRecorder(selected, protocol);
                        Thread.sleep(250);
                    }
                    
                    currentPlayerProcess = startPlayer(selected, protocol);
                    Thread.sleep(500);
                    
                    sendProtocol(protocol);
                    sendRecordFlag(recordEnabled);
                    monitorPlaybackAndLog(selected, protocol, currentPlayerProcess);
                }else //for TCP server a listen socket opens first, so we send the protocol first
                {
                    sendProtocol(protocol);
                    sendRecordFlag(recordEnabled);
                    Thread.sleep(1500);

                    if(recordEnabled){
                        currentRecorderProcess = startRecorder(selected, protocol);
                        Thread.sleep(500);
                    }

                    currentPlayerProcess = startPlayer(selected, protocol);
                    Thread.sleep(500);
                    monitorPlaybackAndLog(selected, protocol, currentPlayerProcess);
                }
                
                if(currentPlayerProcess == null){
                    showAlert("Play Error", "Failed to start player.");
                    return;
                }
                
                currentSelectedVideo = selected;
                currentProtocol = protocol;
                currentRecordEnabled = recordEnabled;
                currentFormat = selected.substring(selected.lastIndexOf(".") + 1);
                currentRes = extractResolution(selected);
                
                if(adaptiveSelected){
                    playbackActive = true;
                    adaptiveEnabled = true;
                    
                    if(adaptiveThread != null && adaptiveThread.isAlive()) adaptiveThread.interrupt();
                    
                    startAdaptiveMonitor();
                }
                
            }catch(Exception ex){
                ex.printStackTrace();
                showAlert("Play Error", "Failed to start playback.");
            }
        });
        
        //3.
        exit.setOnAction(e -> shutdown());
        stage.setOnCloseRequest(e -> shutdown());
        
        play.setPrefWidth(100);
        exit.setPrefWidth(100);
        
        //play and exit button correction 
        HBox btn1 = new HBox(15, play, exit);
        btn1.setAlignment(Pos.BOTTOM_CENTER);
        
        HBox protocolBoxRow = new HBox(10, new Label("Protocol:"), protocolBox);
        protocolBoxRow.setAlignment(Pos.CENTER);
       
        VBox root = new VBox(10, 
                new Label("Select Format"),
                speedLabel,
                bitrateTable,
                formatBox,
                loadVideos,
                videoList,
                protocolBoxRow,
                recordBox,
                adaptiveBox,
                btn1
        );
        
        root.setAlignment(Pos.CENTER);
        root.setFillWidth(true);
        
        Scene scene = new Scene(root, 700, 500);

        stage.setScene(scene);
        stage.setTitle("Streaming Client");
        stage.show();
    }
    
    private void sendProtocol(String protocol){
        try{
            client.sendProtocol(protocol);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private void sendRecordFlag(boolean recordEnabled){
        try{
            client.sendRecordFlag(recordEnabled);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    private String chooseDefaultProtocol(String selectedVideo){
        if(selectedVideo.contains("240")){
            return "TCP";
        }else if(selectedVideo.contains("360") || selectedVideo.contains("480")){
            return "UDP";
        }else{
            return "RTP/UDP";
        }
    }
    
    private void shutdown(){
        try{
            
            adaptiveEnabled = false;
            playbackActive = false;
            switchProgress = false;
            
            if(adaptiveThread != null && adaptiveThread.isAlive()) adaptiveThread.interrupt();
            
            if(client != null) client.close();
            
            if(currentPlayerProcess != null && currentPlayerProcess.isAlive()){
                Thread.sleep(1000);
                currentPlayerProcess.destroy();
                currentPlayerProcess.waitFor();
            } 
            
            if(currentRecorderProcess != null && currentRecorderProcess.isAlive()){
                Thread.sleep(1000);
                currentRecorderProcess.destroy();
                currentRecorderProcess.waitFor();
            }        
        } catch(Exception e){
            e.printStackTrace();
        }
        
        Platform.exit();
        System.exit(0);
        
    }
    
    private Process startPlayer(String selectedVideo, String protocol){
        try{
            if("TCP".equalsIgnoreCase(protocol)){
                ProcessBuilder pb = new ProcessBuilder(
                        "ffplay",
                        "-autoexit",
                        "tcp://127.0.0.1:" + client.getTcpPlaybackPort()
                );
                pb.inheritIO();
                return pb.start();
            }else if("UDP".equalsIgnoreCase(protocol)){
                ProcessBuilder pb = new ProcessBuilder(
                        "ffplay",
                        "-autoexit",
                        "-infbuf",
                        "udp://127.0.0.1:" + client.getUdpPlaybackPort() + "?fifo_size=5000000&overrun_nonfatal=1"
                );
                pb.inheritIO();
                return pb.start();
                
            }else{// RTP/UDP
                ProcessBuilder pb = new ProcessBuilder(
                        "ffplay",
                        "-autoexit",
                        "-infbuf",
                        "udp://127.0.0.1:" + client.getRtpPlaybackPort() + "?fifo_size=5000000&overrun_nonfatal=1"
                );
                pb.inheritIO();
                return pb.start();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
    
    private TextArea createBitrateTable(){
        TextArea table = new TextArea();
        
        table.setEditable(false);
        table.setFocusTraversable(false);
        table.setPrefRowCount(6);
        table.setPrefColumnCount(65);
        
        table.setStyle(
                "-fx-font-family: 'Consolas';" +
                "-fx-font-size: 14;" +
                "-fx-control-inner-background: white;" +
                "-fx-border-color: gray;" +
                "-fx-padding: 10;"
        );
        
        table.setText(
              "Resolution         240p        360p        480p        720p        1080p\n" +
              "                   426 x 240   640 x 360   854 x 480   1280x720   1920x1080\n" +
              "Video Bitrates\n" +
              "Maximum            700 Kbps    1000 Kbps   2000 Kbps   4000 Kbps   6000 Kbps\n" +
              "Recommended        400 Kbps    750 Kbps    1000 Kbps   2500 Kbps   4500 Kbps\n" +
              "Minimum            300 Kbps    400 Kbps    500 Kbps    1500 Kbps   3000 Kbps"
        );
        
        return table;
    }
    
    private void showAlert(String title, String message){
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void monitorPlaybackAndLog(String selectedVideo, String protocol, Process playerProcess){
        
        if(playerProcess == null){
            showAlert("Player Error", "Could not start ffplay");
            return;
        }
        
        final Process watchedPlayer = playerProcess;
        final Process watchedRecorder = this.currentRecorderProcess;
        final String watchedVideo = selectedVideo;
        final String watchedProtocol = protocol;
        
        new Thread(()-> {
           long start = System.currentTimeMillis();
           
           try{
               int exitCode = watchedPlayer.waitFor();
               long end = System.currentTimeMillis();
               
               if(watchedPlayer != currentPlayerProcess){
                   System.out.println("Playback interrupted (adaptive switch)");
                   return;
               }
               
               if(watchedRecorder != null && watchedRecorder.isAlive()){
                   watchedRecorder.destroy();
                   watchedRecorder.waitFor();
               }
               
               long durationSeconds = TimeUnit.MILLISECONDS.toSeconds(end - start);
               String resolution = extractResolution(watchedVideo);
               int bitrate = bitrateResolution(resolution);
               
               statsLogger.logPlaybackSession(watchedVideo, resolution, watchedProtocol, measuredSpeedMbps, bitrate, durationSeconds);
               
               switchProgress = false;
               playbackActive = false;
               adaptiveEnabled = false;

               if(adaptiveThread != null && adaptiveThread.isAlive()) adaptiveThread.interrupt();
               
               if(exitCode != 0){
                   System.out.println("Playback ended with exit code: " + exitCode);
               }else{
                   System.out.println("Playback finished normally");
               }              
           }catch(InterruptedException e){
               Thread.currentThread().interrupt();
           }
        }).start();
    }
    
    private String extractResolution(String videoName){
        if(videoName.contains("240")) return "240";
        if(videoName.contains("360")) return "360";
        if(videoName.contains("480")) return "480";
        if(videoName.contains("720")) return "720";
        if(videoName.contains("1080")) return "1080";
        
        return "unknown";
    }
    
    private int bitrateResolution(String resolution){
        switch(resolution){
            case "240":
                return 400;
            case "360":
                return 750;
            case "480":
                return 1000;
            case "720":
                return 2500;
            case "1080":
                return 4500;
            default:
                return 0;
        }
    }
    
    private Process startRecorder(String selectedVideo, String protocol) throws IOException{
        try{
            
            Path recordingsDirectory = Paths.get("recordings");
            if(!Files.exists(recordingsDirectory)) Files.createDirectories(recordingsDirectory);
            
            //log recordings
            Path logDirectory = Paths.get("logs");
            if(!Files.exists(logDirectory)) Files.createDirectories(logDirectory);
            
            String safeName = selectedVideo.replaceAll("[^a-zA-Z0-9._-]", "_");
            long quick = System.currentTimeMillis();
            
            String outputFile = "recordings/" + quick + "_" + safeName + ".ts";
            String logFile = "logs/recorder_" + quick + ".log";
            
            ProcessBuilder pb;
            
            if("TCP".equalsIgnoreCase(protocol)){
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-i", "tcp://127.0.0.1:" + client.getTcpRecordPort(),
                        "-c", "copy",
                        outputFile
                );
            } else if("UDP".equalsIgnoreCase(protocol)){
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-i", "udp://127.0.0.1:" + client.getUdpRecordPort() + "?fifo_size=5000000&overrun_nonfatal=1",
                        "-c", "copy",
                        outputFile
                );
            } else{
                pb = new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-i", "udp://127.0.0.1:" + client.getRtpRecordPort() + "?fifo_size=5000000&overrun_nonfatal=1",
                        "-c", "copy",
                        outputFile
                );
            }
            
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(logFile));
            
            System.out.println("Recording to file: " + outputFile);
            System.out.println("recorder log: " + logFile);
            
            return pb.start();
            
        }catch(Exception e){
            e.printStackTrace();
            showAlert("Recording Error", "Failed to start recording.");
            return null;
        }
    }
 
    //adaptive methods
    
    private String getResForBandwidth(double speedMbps){ //target resolution
        if(speedMbps >= 6.0) return "1080";
        if(speedMbps >= 4.0) return "720";
        if(speedMbps >= 2.0) return "480";
        if(speedMbps >= 1.0) return "360";
        return "240";
    }
    
    private void startAdaptiveMonitor(){
        adaptiveEnabled = true;
        
        adaptiveThread = new Thread(() -> {
           SpeedTestService speedTestService = new SpeedTestService();
           long lastAdaptiveLogTime = 0;
           
           while(adaptiveEnabled && playbackActive){
               try{
                   Thread.sleep(10000);
                   
                   if(switchProgress) continue;
                   
                   double newSpeed = speedTestService.downloadSpeedMbps();
                   if(newSpeed > 10.0) newSpeed = 5.0;
                   
                   long now = System.currentTimeMillis();
                   if(now - lastAdaptiveLogTime > 5000){
                       System.out.println("[Adaptive] Speed: " + String.format("%.2f", newSpeed) + " Mbps");
                       lastAdaptiveLogTime = now;
                   }
                   
                   String targetRes = getResForBandwidth(newSpeed);
                   
                   StreamingClient tempClient = new StreamingClient("localhost", 4000);
                   List<String> availableVideos = tempClient.requestVideos(newSpeed, currentFormat);
                   
                   String bestTarget = bestVariant(availableVideos, currentSelectedVideo, targetRes);
                   tempClient.close();
                   
                   if(bestTarget == null) continue;
                   
                   if(!bestTarget.equals(currentSelectedVideo)){
                       System.out.println("Adaptive switch enabled: " + currentRes + " -> " + extractResolution(bestTarget));
                       double finalSpeed = newSpeed;
                       String finalTargetRes = extractResolution(bestTarget);
                       Platform.runLater(() -> switchStreamQuality(finalTargetRes, finalSpeed));
                   }
               }catch(InterruptedException e){
                   Thread.currentThread().interrupt();
                   break;
               }catch(Exception e){
                   e.printStackTrace();
               }
           }
        });
        
        adaptiveThread.setDaemon(true);
        adaptiveThread.start();
    }
    
    private void switchStreamQuality(String newRes, double newSpeedMbps){
        try{
            if(!playbackActive || switchProgress) return;
            
            switchProgress = true;

            StreamingClient newClient = new StreamingClient("localhost", 4000);
            List<String> availableVideos = newClient.requestVideos(newSpeedMbps, currentFormat);

            String newVideoName = bestVariant(availableVideos, currentSelectedVideo, newRes);

            if(newVideoName == null){
                newClient.close();
                switchProgress = false;
                return;
            }

            if(newVideoName.equals(currentSelectedVideo)){
                newClient.close();
                switchProgress = false;
                return;
            }

            System.out.println("\nSwitching to: " + newVideoName);
                        
            Process oldPlayer = currentPlayerProcess;
            Process oldRecorder = currentRecorderProcess;
            
            //disconnecting current processes for old player to understand that its the old stream
            currentPlayerProcess = null;
            currentRecorderProcess = null;

            if(oldPlayer != null && oldPlayer.isAlive()){
                oldPlayer.destroy();
                oldPlayer.waitFor();
            }
            
            if(oldRecorder != null && oldRecorder.isAlive()){
                oldRecorder.destroy();
                oldRecorder.waitFor();
            }

            if(client != null) client.close();

            client = newClient;
            client.selectVideo(newVideoName);
            sendProtocol(currentProtocol);
            sendRecordFlag(currentRecordEnabled);

            if("UDP".equalsIgnoreCase(currentProtocol) || "RTP/UDP".equalsIgnoreCase(currentProtocol)){

                Thread.sleep(2000);
                if(currentRecordEnabled){
                    currentRecorderProcess = startRecorder(newVideoName, currentProtocol);
                    Thread.sleep(500);
                }
                //keeping the same player    
                currentPlayerProcess = startPlayer(newVideoName, currentProtocol);
                Thread.sleep(500);

                monitorPlaybackAndLog(newVideoName, currentProtocol, currentPlayerProcess);

            }else{ //TCP
                Thread.sleep(1200);
                if(currentRecordEnabled){
                    currentRecorderProcess = startRecorder(newVideoName, currentProtocol);
                    Thread.sleep(500);
                }

                currentPlayerProcess = startPlayer(newVideoName, currentProtocol);
                Thread.sleep(500);
                monitorPlaybackAndLog(newVideoName, currentProtocol, currentPlayerProcess);
            }

            currentSelectedVideo = newVideoName;
            currentRes = extractResolution(newVideoName);
            measuredSpeedMbps = newSpeedMbps;
            currentFormat = newVideoName.substring(newVideoName.lastIndexOf(".") + 1);
            
            switchProgress = false;

        }catch (Exception e){
            switchProgress = false;
            e.printStackTrace();
        }
    }
    
    private String bestVariant(List<String> availableVideos, String currentVideoName, String targetRes){
        int dotIndex = currentVideoName.lastIndexOf(".");
        if(dotIndex == -1) return null;
        
        String exit = currentVideoName.substring(dotIndex); // .mp4, .mkv, .avi
        String base = currentVideoName.substring(0, dotIndex);
        
        String titleBase = base;
        String[] resolutions = {"240", "360", "480", "720", "1080"};
        
        for(String res : resolutions){
            if(base.endsWith("-" + res)){
                titleBase = base.substring(0, base.length() - (res.length() + 1));
                break;
            }
        }
        
        int targetRank = resolutionRank(targetRes);
        
        String bestMatch = null;
        int bestRank = -1;
        
        for(String video : availableVideos){
            if(!video.endsWith(exit)) continue;
            
            int videoDot = video.lastIndexOf(".");
            if(videoDot == -1) continue;
            
            String videoBase = video.substring(0, videoDot);
            String videoRes = extractResolution(video);
            
            if("unknown".equals(videoRes)) continue;
            
            String candidateTitleBase = videoBase;
            for(String res : resolutions){
                if(videoBase.endsWith("-" + res)){
                    candidateTitleBase = videoBase.substring(0, videoBase.length() - (res.length() + 1));
                    break;
                }
            }
            
            if(!candidateTitleBase.equals(titleBase)) continue;
            
            int rank = resolutionRank(videoRes);
            if(rank <= targetRank && rank > bestRank){
                bestRank = rank;
                bestMatch = video;
            }
        }
        return bestMatch;
    }
    
    private int resolutionRank(String resolution){
        switch(resolution){
            case "240": return 0;
            case "360": return 1;
            case "480": return 2;
            case "720": return 3;
            case "1080": return 4;
            default: return -1;
        }
    }
    
    private void reconnectClient() throws IOException{
        if(client != null){
            try{
                client.close();
            }catch(Exception ignored){}
        }
        client = new StreamingClient("localhost", 4000);
    }
    
    public static void main(String[] args){
        launch(args);
    }
}