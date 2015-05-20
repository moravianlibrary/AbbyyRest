package cz.mzk.abbyyrest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.core.Response.*;
import javax.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
/**
 * Created by rumanekm on 5.5.15.
 */
@Path("/ocr")
public class AbbyyRest {

    String path = System.getenv("ABBYY_IN");

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getList() {
        return listFiles(path);
    }

    @POST
    @Path("/{extension}/{uuid}")
    @Consumes({"multipart/form-data"})
    @Produces(MediaType.TEXT_PLAIN)
    public Response put(@PathParam("uuid") String uuid, @PathParam("extension") String extension, InputStream is)  {
        if(!(extension.equalsIgnoreCase("jpg")||extension.equalsIgnoreCase("jp2"))){
            return Response.status(Status.BAD_REQUEST).entity("Deklarovan chybny format " + extension + " Je akceprovan jpg nabo jp2.").type("text/plain").build();
        }
        if(!(uuid.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))){
            return Response.status(Status.BAD_REQUEST).entity("Neplatne UUID\"" + uuid + "\".").type("text/plain").build();
        }
        File f = new File(path + uuid + "." + extension);
        try {
            FileUtils.copyInputStreamToFile(is, f);
        } catch (IOException e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Zapis souboru selhal.").type("text/plain").build();
        }
        return Response.status(Status.OK).entity("Pozadavek OCR zarazen do fronty.").type("text/plain").build();
    }



private String listFiles(String path){

    File folder = new File(path);
    File[] listOfFiles = folder.listFiles();
    String result="";

    for (int i = 0; i < listOfFiles.length; i++) {
          result = result + "\n" + listOfFiles[i].getName();
    }

    return result;
}

}
