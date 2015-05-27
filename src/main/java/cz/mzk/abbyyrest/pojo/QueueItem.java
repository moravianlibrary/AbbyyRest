package cz.mzk.abbyyrest.pojo;

/**
 * Created by popelka on 25.5.2015.
 */
public class QueueItem {

    public static final String STATE_PROCESSING = "PROCESSING";
    public static final String STATE_DONE = "DONE";
    public static final String STATE_ERROR = "ERROR";
    public static final String STATE_DELETED = "DELETED";

    private String id;
    private String state;
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }




}
