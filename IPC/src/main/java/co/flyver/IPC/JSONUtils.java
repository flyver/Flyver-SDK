package co.flyver.IPC;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

/**
 * Created by Petar Petrov on 11/26/14.
 */
public class JSONUtils {
    private static Gson mGson = new Gson();
    private static final String TAG = "JSONIPC";

    /**
     * Validates if a string is a valid JSON object
     *
     * @param json - String to be validated
     * @return - boolean, true if the string is a valid JSON object
     */
    public static boolean validateJson(String json) {
        try {
            mGson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }


    private static <T> T fromJSON(String json, Type type) {
        if (validateJson(json)) {
            return mGson.fromJson(json, type);
        } else {
            return null;
        }
    }

    /**
     * Creates a generic type object from JSON
     * @param json - String describing the JSON object
     * @param type - Type derived from TypeToken, created with Google GSON
     * @param <T> - Generic object
     * @return
     */
    public static <T> T deserialize(String json, Type type) {
        T jsonObj;
        jsonObj = co.flyver.IPC.JSONUtils.fromJSON(json, type);
        if (jsonObj == null) {
            throw new NullPointerException("JSON is null");
        }
        return jsonObj;
    }

    /**
     * Creates a JSON from a generic object
     * @param t - Object to be serialzied into JSON
     * @param type - Type derived from TypeToken, created with Google GSON
     * @param <T> - Generic object
     * @return
     */
    public static <T> String serialize(T t, Type type) {
        return mGson.toJson(t, type);
    }

    public static <T> String serialize(T t) {
        return mGson.toJson(t);
    }

}
