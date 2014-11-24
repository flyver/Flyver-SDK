package co.flyver.dataloggerlib;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A {@link android.app.IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class LoggerService implements Runnable {
    protected static final String STOP_COMMAND = "stop";
    protected static final String THREAD_NAME = "LoggerThread";

    protected FileLogger m_flLogger;
    protected String m_sFilename = null;
    protected BlockingQueue m_bqQueue;
    protected Thread m_thThread;

    protected String eventURL, eventMethod, eventToken;

    public LoggerService(Context context) {
        m_bqQueue = new LinkedBlockingQueue(100);
        InitProperties(context);
    }

    public void run() {
        try {
            while (true) {
                Object obj = m_bqQueue.take();

                if (obj.getClass() == SimpleEvent.class) {
                    HandleEventLog((SimpleEvent) obj);
                }
                else if (obj.getClass() == CommandEvent.class) {
                    if (((CommandEvent) obj).EventData.equals(STOP_COMMAND)) {
                        throw new InterruptedException();
                    }
                }
            }
        } catch(InterruptedException ie) {
        } catch(Exception e) {
        } finally {
            this.Dispose();
        }
    }

    public boolean Start() {
        m_thThread = new Thread(this, THREAD_NAME);
        m_thThread.start();
        return true;
    }

    public boolean Stop() {
        return this.Stop(false);
    }

    public boolean Stop(boolean bImmediately) {
        if (m_thThread.getState() == Thread.State.TERMINATED) {
            return false;
        }

        if (bImmediately) {
            m_thThread.interrupt();
            return true;
        }

        if (m_thThread.getState() == Thread.State.RUNNABLE) {
            m_bqQueue.add(CommandEvent.CreateStopCommand());
            return true;
        }

        return false;
    }

    protected void InitProperties(Context context) {
        if (m_sFilename != null)
            return;

        try
        {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

            if (sharedPref.getString(IntCfg.internal_storage_path, null) == null) {
                SharedPreferences.Editor edit = sharedPref.edit();
                edit.putString(IntCfg.internal_storage_path, context.getFilesDir().getAbsolutePath());
                edit.apply();
            }

            boolean bInternalStorage = new Boolean(sharedPref.getString(IntCfg.save_internally, "true")).booleanValue();
            if (!isExternalStorageWritable() && !bInternalStorage) {
                bInternalStorage = true;
            }

            if (bInternalStorage)
                m_sFilename = sharedPref.getString(IntCfg.internal_storage_path, null) + File.separator + sharedPref.getString(IntCfg.local_filename, null);
            else
                m_sFilename = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + sharedPref.getString(IntCfg.local_filename, null);

            m_flLogger = new FileLogger(m_sFilename);

            eventURL = sharedPref.getString(IntCfg.KEY_LOG_URL, "");
            eventToken = sharedPref.getString(IntCfg.KEY_ACCESS_TOKEN, "");
            eventMethod = sharedPref.getString(IntCfg.KEY_LOG_METHOD, "");
        } catch(Exception ex) {
            Log.d("LoggerService", ex.getStackTrace().toString());
        }
    }

    protected boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public void Dispose() {
        if (m_flLogger != null)
            m_flLogger.Dispose();
    }

    public void LogData(SimpleEvent sEvent) {
        m_bqQueue.add(sEvent);
    }

    public void LogData(String eType, String eTags, String eData) {
        m_bqQueue.add(new SimpleEvent(eType, eTags, eData));
    }

    protected void HandleEventLog(SimpleEvent se) {
        HandleEventLog(eventURL, eventMethod, eventToken, se.EventType, se.EventTags, se.EventData);
    }

    protected void HandleEventLog(String eURL, String eMethod, String eToken, String eType, String eTags, String eData) {
        try {
            m_flLogger.WriteLogEntry(eType, eTags, eData);
        } catch(Exception ex) {
            //Log.d("LOG", ex.getMessage());
        }

        eMethod = eMethod.toLowerCase();
        if (eMethod.equals("get"))
            httpGET(eURL, eToken, eType, eTags, eData);
        else if (eMethod.equals("post"))
            httpPOST(eURL, eToken, eType, eTags, eData);
    }

    protected String httpGET(String eURL, String eToken, String eType, String eTags, String eData) {
        BufferedReader in = null;
        StringBuffer sb = null;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet();
            request.setURI(new URI(eURL + eToken + "/"
                    + URLEncoder.encode(eType, "UTF-8") + "/"
                    + URLEncoder.encode(eTags, "UTF-8") + "/"
                    + URLEncoder.encode(eData, "UTF-8")));
            HttpResponse response = client.execute(request);
            in = new BufferedReader
                    (new InputStreamReader(response.getEntity().getContent()));
            sb = new StringBuffer("");
            String line = "";
            String NL = System.getProperty("line.separator");
            while ((line = in.readLine()) != null) {
                sb.append(line + NL);
            }
            //in.close();

            // TODO: handle any errors gracefully, storing the data in a file somewhere
        } catch (Exception ex) {
            String asdf = ex.getMessage();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (sb != null)
            return sb.toString();

        return null;
    }

    protected String httpPOST(String eURL, String eToken, String eType, String eTags, String eData) {
        // Create a new HttpClient and Post Header
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(eURL);
        String message = null;

        try {
            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(4);
            nameValuePairs.add(new BasicNameValuePair("token", eToken));
            nameValuePairs.add(new BasicNameValuePair("entrytype", eType));
            nameValuePairs.add(new BasicNameValuePair("entrytags", eTags));
            nameValuePairs.add(new BasicNameValuePair("entrydata", eData));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(httppost);

            message = response.getStatusLine().toString();
        } catch(Exception e)
        {
            message = e.getMessage();
        }

        return message;
    }
}

class CommandEvent extends SimpleEvent {
    public CommandEvent() {
        super("command", "", null);
    }
    public CommandEvent(String command) {
        super("command", "", command);
    }

    public static CommandEvent CreateStopCommand() {
        return new CommandEvent(LoggerService.STOP_COMMAND);
    }
}