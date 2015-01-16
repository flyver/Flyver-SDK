package co.flyver.dataloggerlib;

import android.util.Base64;
import android.util.Log;

/**
 * Created by Valentin Ivanov on 1.11.2014 г..
 */
public class SimpleEvent extends Object {
    protected String EventType;
    protected String EventTags;
    protected String EventData;
    protected long EventTimeStamp;
    protected int Base64EncMask = Base64.NO_WRAP | Base64.NO_PADDING;

    public SimpleEvent() {
        EventType = null;
        EventTags = null;
        EventData = null;
        EventTimeStamp = 0;
    }

    public SimpleEvent(String base64Encoded) {
        String[] arr = base64Encoded.trim().split("\r\n");

        if (arr.length > 0) {
            //Log.i("SimpleEvent.base64", "EventType: " + arr[0]);
            EventType = new String(Base64.decode(arr[0], Base64EncMask));
        }

        if (arr.length > 1) {
            //Log.i("SimpleEvent.base64", "EventTags: " + arr[1]);
            EventTags = new String(Base64.decode(arr[1], Base64EncMask));
        }

        if (arr.length > 2) {
            //Log.i("SimpleEvent.base64", "EventData: " + arr[2]);
            EventData = new String(Base64.decode(arr[2], Base64EncMask));
        }

        if (arr.length > 3)
            try {
                EventTimeStamp = Long.parseLong(new String(Base64.decode(arr[3], Base64EncMask)));
            } catch (Exception ex) {
                ex.printStackTrace();
                EventTimeStamp = System.currentTimeMillis();
            }
    }

    public SimpleEvent(String evType, String evTags, String evData) {
        EventType = evType;
        EventTags = evTags;
        EventData = evData;
        EventTimeStamp = System.currentTimeMillis();
    }

    public SimpleEvent(String evType, String evTags, String evData, long evTicks) {
        EventType = evType;
        EventTags = evTags;
        EventData = evData;
        EventTimeStamp = evTicks;
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

    public Long getEventTimeStamp() {
        return this.EventTimeStamp;
    }

    public void setEventTimeStamp(Long val) {
        this.EventTimeStamp = val;
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

        arr = ("" + this.EventTimeStamp).getBytes();
        sRet += Base64.encodeToString(arr, Base64EncMask) + "\r\n";

        return sRet;
    }
}
