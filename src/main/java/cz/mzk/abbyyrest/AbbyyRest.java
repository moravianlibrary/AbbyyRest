package cz.mzk.abbyyrest;


import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
/**
 * Created by rumanekm on 5.5.15.
 */
@Path("/ocr")
public class AbbyyRest {

    String path = "C:\\IMGSTR\\IN\\";

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getList() {
        return listFiles("C:\\");
    }

    @POST
    @Path("/upload")
    @Consumes({"multipart/form-data"})
    @Produces(MediaType.TEXT_PLAIN)
    public String put(@FormDataParam("file") InputStream is,
                      @FormDataParam("file") FormDataContentDisposition info) {
        File pictureIn = new File(path + info.getFileName());
        try {
            FileUtils.copyInputStreamToFile(is, pictureIn);
        } catch (IOException e) {
            e.printStackTrace();
            return "neni tam";
        }
        return "je tam";
    }



private String listFiles(String path){

    File folder = new File(path);
    File[] listOfFiles = folder.listFiles();
    String result="";

    for (File listOfFile : listOfFiles) {
        result = result + "\n" + listOfFile.getName();
    }

    return result;
}

}
