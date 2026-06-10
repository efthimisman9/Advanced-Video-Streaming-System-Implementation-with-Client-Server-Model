package multimedia;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SupportedMedia {
    public static final List<String> SUPPORTED_FORMATS = Arrays.asList("avi", "mp4", "mkv");
    public static final List<String> SUPPORTED_RESOLUTIONS = Arrays.asList("240", "360", "480", "720", "1080");
    public static final Map<String, String> RESOLUTION_TO_SCALE = new LinkedHashMap<>();
    
	//resolution to scale mapping
    static{
        RESOLUTION_TO_SCALE.put("240", "426:240");
        RESOLUTION_TO_SCALE.put("360", "640:360");
        RESOLUTION_TO_SCALE.put("480", "854:480");
        RESOLUTION_TO_SCALE.put("720", "1280:720");
        RESOLUTION_TO_SCALE.put("1080", "1920:1080");
    }
    
    private SupportedMedia(){

    }
    
    public static boolean isSupportedFormat(String format){
        return format != null && SUPPORTED_FORMATS.contains(format.toLowerCase());
    }
    
    public static boolean isSupportedResolution(String resolution){
        return resolution != null && SUPPORTED_RESOLUTIONS.contains(resolution);
    }
    
    public static String getScale(String resolution){
        return RESOLUTION_TO_SCALE.get(resolution);
    }
    
    public static int resolutionRank(String resolution){
        return SUPPORTED_RESOLUTIONS.indexOf(resolution);
    }
    
    public static boolean isResolutionLessOrEqual(String candidate, String max){
        int candidateRank = resolutionRank(candidate);
        int maxRank = resolutionRank(max);
        
        return candidateRank != -1 && maxRank != -1 && candidateRank <= maxRank;
    }
}
