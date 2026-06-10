package multimedia;

public class VideoFile {
    //variables declaration
    private String name;
    private String format;
    private String resolution;
    private String path;

	//constuctor
    public VideoFile(String name, String format, String resolution, String path){
        this.name = name;
        this.format = format;
        this.resolution = resolution;
        this.path = path;
    }

    //Getters for the name, format, resolution and path
    public String getName(){
        return name;
    }

    public String getFormat(){
        return format;
    }

    public String getResolution(){
        return resolution;
    }

    public String getPath(){
        return path;
    }
    
    public String getFileName(){
        return name + "." + format;
    }

    @Override
    public String toString(){
        return getFileName();
    }
}
