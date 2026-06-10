package multimedia;

import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;
import fr.bmartel.speedtest.SpeedTestReport;


public class SpeedTestService {
    
	//5-second download speedtest
    public double downloadSpeedMbps(){
        
        final SpeedTestSocket speedTestSocket = new SpeedTestSocket();
        final double[] speedMbps = {0.0};
        final boolean[] finished = {false};
        
        speedTestSocket.addSpeedTestListener(new ISpeedTestListener(){
            @Override
            public void onCompletion(SpeedTestReport report){
                speedMbps[0] = report.getTransferRateBit().doubleValue() / 1_000_000.0;
                finished[0] = true;
            }
            
            @Override
            public void onError(SpeedTestError speedTestError, String errorMessage){
                System.out.println("Speed test error: " + errorMessage);
                finished[0] = true;
            }
            
            @Override
            public void onProgress(float percent, SpeedTestReport report){
                speedMbps[0] = report.getTransferRateBit().doubleValue() / 1_000_000.0;
            }
        });
        
        try{
            System.out.println("\nRunning 5-second speed test...");
            speedTestSocket.startDownload("http://127.0.0.1:5050/speedtest");
            Thread.sleep(5000);
            speedTestSocket.forceStopTask();
            Thread.sleep(500);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            speedTestSocket.forceStopTask();
            return speedMbps[0] > 0 ? speedMbps[0] : 2.0;
        }catch(Exception e){
            e.printStackTrace();
            return 2.0;
        }
        
        if(speedMbps[0] <= 0) speedMbps[0] = 2.0;
        
        System.out.println("\nEstimated download speed: " + speedMbps[0] + " Mbps");
        return speedMbps[0];
    }
}
