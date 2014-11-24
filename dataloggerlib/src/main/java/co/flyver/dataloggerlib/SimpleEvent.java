package co.flyver.dataloggerlib;

import android.util.Base64;

/**
 * Created by Valentin Ivanov on 1.11.2014 г..
 */
public class SimpleEvent extends Object {
    protected String EventType;
    protected String EventTags;
    protected String EventData;
    protected int Base64EncMask = Base64.NO_WRAP | Base64.NO_PADDING;

    public SimpleEvent() {
        EventType = null;
        EventTags = null;
        EventData = null;
    }

    public SimpleEvent(String base64Encoded) {
        String[] arr = base64Encoded.split("\r\n");

        if (arr.length > 0)
            EventType = Base64.decode(arr[0], Base64EncMask).toString();

        if (arr.length > 1)
            EventTags = Base64.decode(arr[1], Base64EncMask).toString();

        if (arr.length > 2)
            EventData = Base64.decode(arr[2], Base64EncMask).toString();
    }

    public SimpleEvent(String evType, String evTags, String evData) {
        EventType = evType;
        EventTags = evTags;
        EventData = evData;
    }

    public String getEventType() {
        return this.EventType;
    }

    public void setEventType(String val) {
        this.EventType = val;
    }

    public String getEventTags() {
        return this.EventTags;
    }

    public void setEventTags(String val) {
        this.EventTags = val;
    }

    public String getEventData() {
        return this.EventData;
    }

    public void setEventData(String val) {
        this.EventData = val;
    }

    @Override
    public String toString() {
        String sRet = "";
        byte[] arr = this.EventType.getBytes();
        sRet = Base64.encodeToString(arr, Base64EncMask) + "\r\n";

        arr = this.EventTags.getBytes();
        sRet += Base64.encodeToString(arr, Base64EncMask) + "\r\n";

        arr = this.EventData.getBytes();
        sRet += Base64.encodeToString(arr, Base64EncMask) + "\r\n";

        return sRet;
    }
}
