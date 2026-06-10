package multimedia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

//Utility class for logging playback statistics to a file
public class statsLogger {
	//Shared logger instance
    public static final Logger logger = Logger.getLogger("StatsLogger");
    
    static{
        try{
			//Log directory path and creation of it
            Path logDirectory = Paths.get("logs");
            if(!Files.exists(logDirectory)) Files.createDirectories(logDirectory);
            
			//file handler that appends to the log file
            FileHandler fileHandler = new FileHandler("logs/usage_stats.log", true);
            fileHandler.setFormatter(new Formatter(){
                @Override
                public String format(LogRecord record){
                    return String.format("[%1$tF %1$tT] %2$s%n", record.getMillis(), record.getMessage());
                }
            });
            //disable forwarding log records, attaching file handler to logger, setting minimum logging to INFO
            logger.setUseParentHandlers(false);
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
    private statsLogger(){}
    
    public static void logPlaybackSession(String videoName, String resolution, String protocol,
                                          double measuredSpeedMbps, int bitrateKbps,
                                          long durationSeconds){
        String message = String.format("video=%s | resolution=%s | protocol=%s | speed=%.2f Mbps | bitrate=%d Kbps | playbackDuration=%d sec",
                videoName,
                resolution,
                protocol,
                measuredSpeedMbps,
                bitrateKbps,
                durationSeconds);
        
        logger.info(message);
    }
}
