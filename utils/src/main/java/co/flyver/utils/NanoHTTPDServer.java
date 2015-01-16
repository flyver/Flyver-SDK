package co.flyver.utils;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

import co.flyver.IPC.JSONUtils;
import co.flyver.utils.flyverMQ.FlyverMQ;
import co.flyver.utils.flyverMQ.FlyverMQMessage;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQConsumer;
import fi.iki.elonen.NanoHTTPD;

/**
 * Created by Petar Petrov on 1/6/15.
 */

/**
 * Subclass of NanoHTTPD, used to serve webpages from the smartphone
 */
public class NanoHTTPDServer extends NanoHTTPD {
    private SensorsDataStorage storage;
    public NanoHTTPDServer() {
        super(8080);
        storage = new SensorsDataStorage();
        FlyverMQ.getInstance().registerConsumer(storage, "sensors.raw");
    }

    private class SensorsDataStorage implements FlyverMQConsumer {
        Queue<FlyverMQMessage> messages = new LinkedBlockingDeque<>();

        @Override
        public void dataReceived(FlyverMQMessage message) {
            messages.add(message);
        }

        @Override
        public void unregistered() {

        }

        @Override
        public void paused() {

        }

        @Override
        public void resumed() {

        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        final String sourcePath = "/co.flyver/webpage";
        String mime_type = NanoHTTPD.MIME_HTML;
        Method method = session.getMethod();
        String uri = session.getUri();
        Map<String, String> files = new HashMap<>();
        BufferedReader bufferedReader;
        try {
            session.parseBody(files);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ResponseException e) {
            e.printStackTrace();
        }
        if (method.toString().equalsIgnoreCase("GET")) {
            File sdcard = Environment.getExternalStorageDirectory();
            File resource = new File(sdcard, sourcePath.concat("/index.html"));
            StringBuilder html = new StringBuilder();
            if(uri.endsWith(".html")) {
                mime_type = NanoHTTPD.MIME_HTML;

            } else if(uri.endsWith(".js")) {
                mime_type = "text/javascript";
                resource = new File(sdcard, sourcePath.concat(uri));

            } else if(uri.endsWith(".css")) {
                resource = new File(sdcard, sourcePath.concat(uri));
                mime_type = "text/css";
            }

            try {
                bufferedReader = new BufferedReader(new FileReader(resource));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    html.append(line);
                    html.append("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return new Response(Response.Status.OK, mime_type, html.toString());
        }
        if(method.toString().equalsIgnoreCase("POST")) {
            switch(files.get("postData")) {
                case "sensors": {
                    LinkedList<String> data = new LinkedList<>();
                    for(int i = 0; i < 50 || i == (storage.messages.size() - 1); i++) {
                        try {
                            data.add((String) storage.messages.poll().data);
                        } catch(NullPointerException e) {
                            break;
                        }
                    }
                   return new Response(Response.Status.OK, MIME_PLAINTEXT, JSONUtils.serialize(data));
                }
                default: {
                    return new Response(Response.Status.OK, MIME_PLAINTEXT, "DEFAULT!");
                }
            }
        }
        return new Response(Response.Status.NOT_FOUND, MIME_HTML, "UNAVAILABLE!!!");
    }
}
