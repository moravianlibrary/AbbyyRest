package cz.mzk.abbyyrest;

import cz.mzk.abbyyrest.pojo.QueueItem;
import org.apache.commons.io.FileUtils;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.HttpHeaders;

/**
 * Created by rumanekm on 5.5.15.
 */
@Path("/ocr")
public class AbbyyRest {

    String path = System.getenv("ABBYY_IN");

    @GET
    @Produces(javax.ws.rs.core.MediaType.TEXT_PLAIN)
    public String getList() {
        return listFiles(path);
    }

    @POST
    @Path("/in")
    @Consumes({"image/jpeg", "image/jp2"})
    @Produces("application/json")
    public Response put(InputStream is, @Context HttpHeaders headers) {
        String contentType = headers.getRequestHeader("Content-Type").get(0);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Nepodarilo se inicializovat vypocet MD5.").type("text/plain").build();
        }

        DigestInputStream dis = new DigestInputStream(is, md);
        File fnoname = new File(path + "forming.tmp");
        try {
            FileUtils.copyInputStreamToFile(dis, fnoname);
        } catch (IOException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Zapis souboru selhal.").type("text/plain").build();
        }

        byte[] digest = md.digest();
        StringBuffer nameBuilder = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            String hex = Integer.toHexString(0xff & digest[i]);
            if (hex.length() == 1) nameBuilder.append('0');
            nameBuilder.append(hex);
        }
        String extension = contentType.split("/")[1];
        String name = nameBuilder.toString();
        String fullName = name + "." + extension;
        File fnamed = new File(path + fullName);
        if (!fnoname.renameTo(fnamed)) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Prejmenovani docasneho souboru selhalo.").type("text/plain").build();
        }

        QueueItem pushedItem = new QueueItem();
        pushedItem.setId(fullName);

        return Response.ok(pushedItem, "application/json").build();
    }

    private String listFiles(String path) {

        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        String result = "";

        for (int i = 0; i < listOfFiles.length; i++) {
            result = result + "\n" + listOfFiles[i].getName();
        }

        return result;
    }

}
