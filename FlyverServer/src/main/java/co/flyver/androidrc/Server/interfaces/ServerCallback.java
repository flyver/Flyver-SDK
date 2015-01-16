package co.flyver.androidrc.Server.interfaces;

/**
 * Created by Petar Petrov on 1/6/15.
 */

/**
 * Interface for defining custom callbacks
 * which are associated with keys, and are run
 * when a JSON with the appropriate key is received
 */
public interface ServerCallback {
    public void run(String json);
}

