package multimedia;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;

import java.util.List;
import java.io.File;
import java.nio.file.Path;

public class VideoVariantGenerator {
    
	//root folder
    private final String videosFolder;
    
	//target video folder
    public VideoVariantGenerator(String videosFolder){
        this.videosFolder = videosFolder;
    }
    
	/*iterates all video groups and generates any missing resolution/format variants
	  a variant is skipped if it already exists*/
    public void generateMissingVariants(List<VideoGroup> groups){
        for(VideoGroup group : groups){
            String maxResolution = group.getMaxResolution();
            VideoFile bestSource = group.getBestSourceFIle();
            
			//skip group if no valid source
            if(maxResolution == null || bestSource == null){
                System.out.println("Skipping group with no valid source: " + group.getTitle());
                continue;
            }
            
            System.out.println("Processing title: " + group.getTitle());
            System.out.println("Max allowed resolution: " + maxResolution);
            System.out.println("Best source: " + bestSource.getFileName());
            
			//iterate all supported resolutions
            for(String resolution : SupportedMedia.SUPPORTED_RESOLUTIONS){
                if(!SupportedMedia.isResolutionLessOrEqual(resolution, maxResolution)) continue;
                
				//iterate over all supported output formats
                for (String format : SupportedMedia.SUPPORTED_FORMATS) {
                    if (group.hasVariant(resolution, format)) continue;
					
					//build output file name and full path
                    String outputFileName = group.getTitle() + "-" + resolution + "." + format;
                    File outputFile = new File(videosFolder, outputFileName);

                    System.out.println("Creating missing variant: " + outputFile.getAbsolutePath());

                    try {
                        createVariant(bestSource.getPath(), outputFile.toPath(), resolution);
                        System.out.println("Created: " + outputFileName);
                    } catch (Exception e) {
                        System.out.println("Failed to create: " + outputFileName);
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    private void createVariant(String inputPath, Path outputPath, String resolution){
        String scale = SupportedMedia.getScale(resolution);
        
		//execute FFmpeg command with scale filter
        FFmpeg.atPath().
                addInput(UrlInput.fromPath(new File(inputPath).toPath())).
                addOutput(UrlOutput.toPath(outputPath).
                        addArguments("-vf", "scale=" + scale)).
                execute();
    }
}
