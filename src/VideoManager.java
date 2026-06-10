package multimedia;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import multimedia.SupportedMedia;

public class VideoManager {
    private List<VideoFile> videos = new ArrayList<>();

    public void loadVideos(String folderPath){
        
		//clear previously loaded videos before reloading
        videos.clear();

        File folder = new File(folderPath);
        System.out.println("Loading videos from: " + folder.getAbsolutePath());
        
		//List all files to the given directory
        File[] files = folder.listFiles();
        
        if(files == null){
            System.out.println("No files found");
            return;
        }
        
        System.out.println("Files found: " + files.length);

        for(File file : files){
            
			//skip directories, process only regular files
            if(!file.isFile()) continue;
            
            String fileName = file.getName(); 
            int doIndex = fileName.lastIndexOf("."); //find the position of extension
            
            if(doIndex == -1){ //skip files that have no extension
                System.out.println("Skipping file without extension: " + fileName);
                continue;
            }
            
            try{
				//extract file name and extension 
                String name = fileName.substring(0, doIndex);
                String format = fileName.substring(doIndex + 1).toLowerCase();
                
				//skip unsupported formats
                if(!SupportedMedia.isSupportedFormat(format)){
                    System.out.println("Skipping unsupported format: " +fileName);
                    continue;
                }
                
                String resolution = getVideoResolution(file.getAbsolutePath());
                
                videos.add(new VideoFile(name, format, resolution, file.getAbsolutePath()));
                
                System.out.println("Loaded video: " + fileName + " [" + resolution + "]");
                
            } catch(Exception e){
                System.out.println("Error loading file: " + fileName);
                e.printStackTrace();
            }
        }
    }

	//use ffprobe to read the height of the first video stream 
    private String getVideoResolution(String path) {
        try {

            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=height",
                    "-of", "csv=p=0",
                    path
            );

            Process process = pb.start();

            BufferedReader reader
                    = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));

            String height = reader.readLine();
            process.waitFor();

            if (height == null) {
                return "unknown";
            }

			//parse the height and map it to the nearest resolution
            int h = Integer.parseInt(height.trim());
            
            if(h <= 240){
                return "240";
            }else if(h <= 360){
                return "360";
            }else if(h <= 480) {
                return "480";
            } else if(h <= 720) {
                return "720";
            } else {
                return "1080";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "unknown";
        }
    }

	//search loaded videos
    public VideoFile findVideoByFileName(String fileName){
        for(VideoFile v : videos){
            if(v.getFileName().equalsIgnoreCase(fileName)) return v;
        }
        return null;
    }
    
	//groups loaded videos by their title
    public List<VideoGroup> groupVideoByTitle(){
        List<VideoGroup> groups = new ArrayList<>();
        
        for(VideoFile video : videos){
            VideoGroup existingGroup = null;
            
            for(VideoGroup group : groups){
                if(group.getTitle().equalsIgnoreCase(video.getName())){
                    existingGroup = group;
                    break;
                }
            }
            
            if(existingGroup == null){
                existingGroup = new VideoGroup(video.getName());
                groups.add(existingGroup);
            }
            
            existingGroup.addVariant(video);
        }
        
        return groups;
    }
    
	//filter loaded videos by format and maximum resolution
    public List<VideoFile> getVideosByFormatAndMaxResolution(String format, String maxResolution){
        
        List<VideoFile> result = new ArrayList<>();
        
        for(VideoFile v : videos){
            if(v.getFormat().equalsIgnoreCase(format) && 
                    SupportedMedia.isResolutionLessOrEqual(v.getResolution(), maxResolution)){
                result.add(v);
            }
        }
        
        return result;
    }
}
