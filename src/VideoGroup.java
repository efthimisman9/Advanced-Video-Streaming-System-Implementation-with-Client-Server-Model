package multimedia;

import java.util.ArrayList;
import java.util.List;


public class VideoGroup {
    
    private String title;
    private List<VideoFile> variants;
    
    public VideoGroup(String title){
        this.title = title;
        this.variants = new ArrayList<>();
    }
    
    public String getTitle(){
        return title;
    }
    
    public List<VideoFile> getVariants(){
        return variants;
    }
    
    public void addVariant(VideoFile videoFile){
        variants.add(videoFile);
    }
    
	//finds the highest supported resolution among the variants, unsupported ones are ignored
    public String getMaxResolution(){
        
        String max = null;
        
        for(VideoFile v : variants){
            String resolution = v.getResolution();
            
            if(!SupportedMedia.isSupportedResolution(resolution)) continue;
            
            if(max == null || SupportedMedia.resolutionRank(resolution) > SupportedMedia.resolutionRank(max))
                max = resolution;
                
        }
        
        return max;
    }
    
	//check if variant exists
    public boolean hasVariant(String resolution, String format){
        for(VideoFile v : variants){
            if(v.getResolution().equalsIgnoreCase(resolution) && v.getFormat().equalsIgnoreCase(format))
                return true;
        }
        return false;
    }
    
	//returns highest supported resolution variant, unsopported are ignored as well
    public VideoFile getBestSourceFIle(){
        VideoFile best = null;
        
        for(VideoFile v : variants){
            if(!SupportedMedia.isSupportedResolution(v.getResolution())) continue;
            
            if(best == null || SupportedMedia.resolutionRank(v.getResolution()) > SupportedMedia.resolutionRank(best.getResolution()))
                best = v;
        }
        
        return best;
    }
  
}
