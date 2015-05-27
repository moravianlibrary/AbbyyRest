package cz.mzk.abbyyrest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.HttpHeaders;

import cz.mzk.abbyyrest.pojo.QueueItem;
import org.apache.commons.io.FileUtils;

/**
 * Created by rumanekm on 5.5.15.
 */
@Path("/ocr")
public class AbbyyRest {

    String pathIn = System.getenv("ABBYY_IN");
    String pathOut = System.getenv("ABBYY_OUT");
    String pathEx = System.getenv("ABBYY_EXCEPTION");

    @GET
    @Path("/state/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getState(@PathParam("id") String id) {

        QueueItem searchedItem = new QueueItem();
        searchedItem.setId(id);
        searchedItem.setState(QueueItem.STATE_ERROR);

        if (!id.matches("[a-fA-F0-9]{32}")) {
            searchedItem.setMessage("Zadane ID nema validni tvar MD5.");
            return Response.status(Status.BAD_REQUEST).entity(searchedItem).build();
        }
        File txt = isPresentIn(id, "txt", pathOut);
        File xml = isPresentIn(id, "xml", pathOut);

        if (xml != null && txt != null) {
            searchedItem.setState(QueueItem.STATE_DONE);
            searchedItem.setMessage("Zpracovano.");
        } else if (xml != null) {
            searchedItem.setMessage("Ve vystupni slozce je pritomny pouze vystup ALTO.");
        } else if (txt != null) {
            searchedItem.setMessage("Ve vystupni slozce je pritomny pouze vystup TXT.");
        } else if (!(isPresentIn(id, pathEx) == null)) {
            searchedItem.setMessage("Beh ABBYY OCR skoncil vyjimkou.");
        } else if (!(isPresentIn(id, pathIn) == null)) {
            searchedItem.setState(QueueItem.STATE_PROCESSING);
            searchedItem.setMessage("Ve fronte ke zpracovani.");
        } else {
            searchedItem.setMessage("Zadny zaznam pro zadane ID.");
            return Response.status(Status.NOT_FOUND).entity(searchedItem).build();
        }
        return Response.ok(searchedItem).build();
    }

    @POST
    @Path("/in")
    @Consumes({"image/jpeg", "image/jp2"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response putInQueue(InputStream is, @Context HttpHeaders headers) {

        String contentType = headers.getRequestHeader("Content-Type").get(0);
        MessageDigest md;
        QueueItem pushedItem = new QueueItem();
        pushedItem.setId(null);
        pushedItem.setState(QueueItem.STATE_ERROR);

        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            pushedItem.setMessage("Nepodarilo se inicializovat vypocet MD5.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }

        DigestInputStream dis = new DigestInputStream(is, md);
        File fnoname = new File(pathIn + "forming.tmp");
        try {
            FileUtils.copyInputStreamToFile(dis, fnoname);
        } catch (IOException e) {
            pushedItem.setMessage("Zapis souboru do vstupni slozky selhal.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }

        byte[] digest = md.digest();
        StringBuffer nameBuilder = new StringBuffer();
        for (int i = 0; i < digest.length; i++) {
            String hex = Integer.toHexString(0xff & digest[i]);
            if (hex.length() == 1) nameBuilder.append('0');
            nameBuilder.append(hex);
        }
        String extension = contentType.split("/")[1];
        if (extension.equalsIgnoreCase("jpeg")) {
            extension = "jpg";
        }
        String name = nameBuilder.toString();
        pushedItem.setId(name);
        String fullName = name + "." + extension;
        File fnamed = new File(pathIn + fullName);
        if (fnamed.exists()) {
            pushedItem.setMessage("Polozka je jiz ve vstupni slozce.");
            return Response.status(Status.CONFLICT).entity(pushedItem).build();
        }
        if (!fnoname.renameTo(fnamed)) {
            fnoname.delete();
            pushedItem.setMessage("Prejmenovani docasneho souboru selhalo.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }

        pushedItem.setState(QueueItem.STATE_PROCESSING);
        pushedItem.setMessage("Zarazeno do fronty.");
        return Response.ok(pushedItem).build();
    }

    @GET
    @Path("/product/{type}/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN})
    public Response getProduct(@PathParam("id") String id, @PathParam("type") String type) {

        QueueItem requestedItem = new QueueItem();
        requestedItem.setId(id);
        requestedItem.setState(QueueItem.STATE_ERROR);

        String mType;
        if (type.equalsIgnoreCase("alto")) {
            mType = MediaType.TEXT_XML;
            type = "xml";
        } else if (type.equalsIgnoreCase("txt")) {
            mType = MediaType.TEXT_PLAIN;
        } else {
            requestedItem.setMessage("Byl pozadovan jiny format nez \"alto\" nebo \"txt\".");
            return Response.status(Status.BAD_REQUEST).entity(requestedItem).build();
        }


        if (!id.matches("[a-fA-F0-9]{32}")) {
            requestedItem.setMessage("Zadane ID nema validni tvar MD5.");
            return Response.status(Status.BAD_REQUEST).entity(requestedItem).build();
        }

        File f = isPresentIn(id, type, pathOut);

        if (f == null) {
            requestedItem.setMessage("Pozadovany produkt neni k dispozici");
            return Response.status(Status.NOT_FOUND).entity(requestedItem).type(MediaType.APPLICATION_JSON).build();
        } else {
            FileInputStream fis;
            try {
                fis = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                requestedItem.setMessage("Soubor se nepodarilo precist");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(requestedItem).type(MediaType.APPLICATION_JSON).build();
            }
            return Response.ok().entity(fis).type(mType).build();

        }
    }

    @POST
    @Path("/delete/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response delete(@PathParam("id") String id) {
        QueueItem deletedItem = new QueueItem();
        deletedItem.setState(QueueItem.STATE_ERROR);
        String message = "";

        File f = isPresentIn(id, pathIn);
        if (f != null) {
            if (f.delete()) {
                message = message + " IN";
            } else {
                deletedItem.setMessage("Selhalo smazani ze slozky IN.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
            }
        }

        f = isPresentIn(id, "txt", pathOut);
        if (f != null) {
            if (f.delete()) {
                f = isPresentIn(id, "alto", pathOut);
                if (f != null) {
                    if (f.delete()) {
                        message = message + " OUT";
                    } else {
                        deletedItem.setMessage("Selhalo smazani ALTO ze slozky OUT.");
                        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
                    }
                }
            } else {
                deletedItem.setMessage("Selhalo smazani TXT ze slozky OUT.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
            }
        }

        f = isPresentIn(id, pathEx);
        if (f != null) {
            if (f.delete()) {
                message = message + " EXCEPTION";
            } else {
                deletedItem.setMessage("Selhalo smazani ze slozky EXCEPTION.");
            }
        }

        if (message != "") {
            deletedItem.setMessage("Smazano z" + message + ".");
            deletedItem.setState(QueueItem.STATE_DELETED);
            return Response.ok().entity(deletedItem).build();
        }

        deletedItem.setMessage("Polozka nebyla nalezena.");
        return Response.status(Status.NOT_FOUND).entity(deletedItem).build();
    }

    private File isPresentIn(String id, String path) {
        return isPresentIn(id, "", path);
    }

    private File isPresentIn(String id, String suffix, String path) {

        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            for (File f : listOfFiles) {
                String fname = f.getName();
                if (fname.length() >= 36) {
                    if ((fname.substring(0, 32).equalsIgnoreCase(id)) && (fname.endsWith(suffix))) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

}
