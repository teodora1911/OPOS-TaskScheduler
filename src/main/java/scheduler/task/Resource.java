package scheduler.task;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

public class Resource implements Serializable {
    public String path;
    public String name;

    public Resource(File file){
        this.path = file.getAbsolutePath();
        this.name = file.getName();
    }

    public BufferedImage getImage() {
        try{
            return ImageIO.read(new File(path));
        } catch (IOException exc){
            exc.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean equals(Object object){
        if(this == object)
            return true;
        if(object == null)
            return false;
        if(getClass() != object.getClass())
            return false;
        Resource other = (Resource) object;
        return path.equals(other.path);
    }

    @Override
    public int hashCode(){
        return (path != null) ? 13 : path.hashCode();
    }

    @Override
    public String toString() { return path; }
}
