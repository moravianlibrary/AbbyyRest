package cz.mzk.abbyyrest;

import cz.mzk.abbyyrest.pojo.QueueItem;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by rumanekm on 5.5.15.
 */
@Path("/ocr")
public class AbbyyRest {

    static final Logger logger = Logger.getLogger(AbbyyRest.class);

    String pathIn = System.getenv("ABBYY_IN");
    String pathOut = System.getenv("ABBYY_OUT");
    String pathEx = System.getenv("ABBYY_EXCEPTION");
    String pathTmp = System.getenv("ABBYY_TMP");


    @GET
    @Path("/state/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getState(@PathParam("id") String id) {

        QueueItem searchedItem = new QueueItem();
        searchedItem.setId(id);
        logger.info("Ověřuje se stav položky " + id + "...");

        if (!id.matches("[a-fA-F0-9]{32}")) {
            searchedItem.setMessage("Zadané ID nemá validní tvar MD5.");
            logger.warn("Zadané ID " + id + " nemá validní tvar MD5. Vrácena chyba 400.");
            searchedItem.setState(QueueItem.STATE_ERROR);
            return Response.status(Status.BAD_REQUEST).entity(searchedItem).build();
        }
        File txt = isPresentIn(id, "txt", pathOut);
        File xml = isPresentIn(id, "xml", pathOut);

        if (xml != null && txt != null) {
            logger.debug("Pro ID " + id + " existuje textový i ALTO výstup.");
            File deletedFlag = isPresentIn(id, "flg", pathTmp);
            if (deletedFlag != null) {
                deletedFlag.delete();
                logger.debug("Smazán .flg soubor");
            }
            searchedItem.setState(QueueItem.STATE_DONE);
            logger.info("Položka " + id + " je zpracována. Vrácena odpověď 200.");
            searchedItem.setMessage("Zpracováno.");
            return Response.ok(searchedItem).build();

        } else if (isPresentIn(id, "result.xml", pathEx) != null) {
            File deletedFlag = isPresentIn(id, "flg", pathTmp);
            if (deletedFlag != null) {
                deletedFlag.delete();
                logger.debug("Smazán .flg soubor");
            }
            searchedItem.setMessage("Běh ABBYY OCR skončil pro položku " + id + " výjimkou.");
            // TODO cesta k exception
            logger.warn("Běh ABBYY OCR skončil pro položku " + id + " výjimkou. Vrácena chyba 500.");
            searchedItem.setState(QueueItem.STATE_ERROR);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(searchedItem).build();

        } else if (isPresentIn(id, "flg", pathTmp) != null) {
            searchedItem.setState(QueueItem.STATE_PROCESSING);
            searchedItem.setMessage("Ve frontě ke zpracování.");
            logger.info("Položka " + id + " čeká ve frontě ke zpracování. Vrácena odpověď 200.");
            return Response.ok(searchedItem).build();

        } else {
            searchedItem.setMessage("Žádný záznam pro zadané ID.");
            logger.warn("Položka " + id + " nebyla nalezena. Vrácena odpověď 404.");
            searchedItem.setState(QueueItem.STATE_ERROR);
            return Response.status(Status.NOT_FOUND).entity(searchedItem).build();
        }
    }

    @POST
    @Path("/in")
    @Consumes({"image/jpeg", "image/jp2"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response putInQueue(InputStream is, @Context HttpHeaders headers) {

        String contentType = headers.getRequestHeader("Content-Type").get(0);
        logger.info("Přijata žádost na zařazení souboru typu " + contentType + " do fronty ...");
        MessageDigest md;
        QueueItem pushedItem = new QueueItem();
        pushedItem.setId(null);
        pushedItem.setState(QueueItem.STATE_ERROR);

        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            pushedItem.setMessage("Nepodařilo se inicializovat výpočet MD5.");
            logger.error("Nepodařilo se inicializovat výpočet MD5. Vrácena chyba 500.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }

        DigestInputStream dis = new DigestInputStream(is, md);
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        Date date = new Date();
        String tmpname = pathTmp + File.separator + dateFormat.format(date) + ".tmp";
        File tmpfile = new File(tmpname);
        logger.debug("Vytvářím dočasný soubor " + tmpname);
        try {
            FileUtils.copyInputStreamToFile(dis, tmpfile);
        } catch (IOException e) {
            pushedItem.setMessage("Zápis souboru do vstupní složky selhal.");
            logger.error("Zápis dočasného souboru " + tmpname + " selhal. Vrácena chyba 500.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }

        logger.debug("Dočasný soubor " + tmpname + " byl vytvořen.");

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
        logger.debug("Vypočítáno MD5: " + name + " .");
        pushedItem.setId(name);
        String fullName = name + "." + extension;
        File fnamed = new File(pathIn + File.separator + fullName);

        File flg = new File(pathTmp + File.separator + name + ".flg");
        logger.debug("Vytvářím .flg soubor..." + pathTmp + File.separator + name + ".flg");
        try {
            flg.createNewFile();
        } catch (IOException e) {
            pushedItem.setMessage("Vytvoření .flg značky selhalo.");
            logger.error("Vytvoření .flg souboru " + pathTmp + File.separator + name + ".flg" + " selhalo. Vrácena chyba 500.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }
        logger.debug("Soubor " + pathTmp + File.separator + name + ".flg" + " byl vytvořen.");

        if (fnamed.exists()) {
            pushedItem.setState(QueueItem.STATE_PROCESSING);
            pushedItem.setMessage("Položka je již ve vstupní složce.");
            logger.info("Položka " + name + " je již ve vstupní složce.");
            if (tmpfile.delete()) {
                logger.debug("Dočasný soubor " + tmpname + " byl smazán.");
            } else {
                logger.warn("Dočasný soubor " + tmpname + " nebyl smazán.");
            }
//            return Response.status(Status.CONFLICT).entity(pushedItem).build();
            return Response.ok(pushedItem).build();
        }

        if (!tmpfile.renameTo(fnamed)) {

            pushedItem.setMessage("Přesun z dočasné do vstupní složky selhal.");
            logger.error("Přesun z dočasné do vstupní složky selhal. Vrácena chyba 500.");
            if (tmpfile.delete()) {
                logger.debug("Dočasný soubor " + tmpname + " byl smazán.");
            } else {
                logger.warn("Dočasný soubor " + tmpname + " nebyl smazán.");
            }
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(pushedItem).build();
        }
        if (tmpfile.delete()) {
            logger.debug("Dočasný soubor " + tmpname + " byl smazán.");
        } else {
            logger.warn("Dočasný soubor " + tmpname + " nebyl smazán.");
        }
        pushedItem.setState(QueueItem.STATE_PROCESSING);
        pushedItem.setMessage("Zařazeno do fronty.");
        logger.info("Položka " + name + " byla zařazena do fronty. Vrácena odpověď 200." );
        return Response.ok(pushedItem).build();
    }

    @GET
    @Path("/product/{type}/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN})
    public Response getProduct(@PathParam("id") String id, @PathParam("type") String type) {

        logger.info("Přijata žádost o " + type + " výstup položky " + id);
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
            requestedItem.setMessage("Byl požadován jiný formát než \"alto\" nebo \"txt\".");
            logger.warn("Byl požadován jiný formát než \"alto\" nebo \"txt\". Vrácena chyba 400.");
            return Response.status(Status.BAD_REQUEST).entity(requestedItem).build();
        }


        if (!id.matches("[a-fA-F0-9]{32}")) {
            requestedItem.setMessage("Zadané ID nemá validní tvar MD5.");
            logger.warn("ID výstupu " + id + " nemá validní tvar. Vrácena chyba 400.");
            return Response.status(Status.BAD_REQUEST).entity(requestedItem).build();
        }

        File f = isPresentIn(id, type, pathOut);

        if (f == null) {
            requestedItem.setMessage("Požadovaný výstup není k dispozici");
            logger.warn("Výstup není dostupný. Vrácena chyba 400.");
            return Response.status(Status.NOT_FOUND).entity(requestedItem).type(MediaType.APPLICATION_JSON).build();
        } else {
            FileInputStream fis;
            try {
                fis = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                requestedItem.setMessage("Soubor se nepodařilo přečíst");
                logger.error("Soubor" +id + " " + type + " se nepodařilo přečíst. Vrácena chyba 500.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(requestedItem).type(MediaType.APPLICATION_JSON).build();
            }
            logger.info("Výstup " + id + " " + type + " bude odeslán s odpovědí 200.");
            return Response.ok().entity(fis).type(mType).build();

        }
    }

    @DELETE
    @Path("/delete/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response delete(@PathParam("id") String id) {

        logger.info("Přijata žádost o odstraňování položky " + id);
        QueueItem deletedItem = new QueueItem();
        deletedItem.setState(QueueItem.STATE_ERROR);
        String message = "";

        File f = isPresentIn(id, "jpg", pathIn);
        if (f != null) {
            if (f.delete()) {
                message = message + " IN";
                logger.info("Soubor smazán ze složky IN.");
            } else {
                logger.error("Selhalo odstraňování ze složky IN.");
                deletedItem.setMessage("Selhalo odstraňování ze složky IN.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
            }
        }

        f = isPresentIn(id, "flg", pathTmp);
        if (f != null) {
            if (f.delete()) {
                message = message + " TMP";
                logger.info("Smazána .flg značka ze složky TMP.");
            } else {
                deletedItem.setMessage("Selhalo odstraňování flagu ze složky TMP.");
                logger.error("Nepodařilo se smazat .flg značku ze složky TMP.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
            }
        }

        f = isPresentIn(id, "txt", pathOut);
        if (f != null) {
            if (f.delete()) {
                logger.info("Soubor " + id + ".txt smazán ze složky OUT.");
                f = isPresentIn(id, "xml", pathOut);
                if (f != null) {
                    if (f.delete()) {
                        logger.info("Soubor " + id + ".xml smazán ze složky OUT.");
                        f = isPresentIn(id, "result.xml", pathOut);
                        if (f != null) {
                            if (f.delete()) {
                                logger.info("Soubor " + id + ".result smazán ze složky OUT.");
                                message = message + " OUT";
                            } else {
                                logger.error("Odstraňování souboru " + id + ".result ze složky OUT selhalo.");
                                deletedItem.setMessage("Selhalo odstraňování RESULT ze složky OUT.");
                                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
                            }
                        } else {
                            logger.error("Odstraňování ALTO souboru " + id + ".xml ze složky OUT selhalo.");
                            deletedItem.setMessage("Selhalo odstraňování ALTO ze složky OUT.");
                            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
                        }
                    }
                } else {
                    logger.error("Odstraňování souboru " + id + ".txt ze složky OUT selhalo.");
                    deletedItem.setMessage("Selhalo odstraňování TXT ze složky OUT.");
                    return Response.status(Status.INTERNAL_SERVER_ERROR).entity(deletedItem).build();
                }
            }
        }
        f = isPresentIn(id, "xml", pathEx);
        if (f != null) {
            if (f.delete()) {
                logger.info("Soubor smazán  ze složky EXCEPTION.");
                message = message + " EXCEPTION";
            } else {
                logger.error("Odstraňování souboru ze složky EXCEPTION selhalo.");
                deletedItem.setMessage("Selhalo odstraňování ze složky EXCEPTION.");
            }
        }

        if (!message.equals("")) {
            deletedItem.setMessage("Smazáno z " + message);
            deletedItem.setState(QueueItem.STATE_DELETED);
            return Response.ok().entity(deletedItem).build();
        }

        logger.error("Položka nebyla nalezena v žádném z adresařů.");
        deletedItem.setMessage("Položka nebyla nalezena.");
        return Response.status(Status.NOT_FOUND).entity(deletedItem).build();
    }

    // v současném stavu nemůže fungovat (každý soubor má nějaký suffix)
    // TODO opravit mazání z IN (Jaké jsou možné suffixy? Jen .jpg a .jp2?)
    private File isPresentIn(String id, String path) {
        return isPresentIn(id, "", path);
    }

    private File isPresentIn(String id, String suffix, String path) {
        File f = new File(path + File.separator + id + "." + suffix);
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        return null;
    }

}