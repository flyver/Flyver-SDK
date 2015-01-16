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
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

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

import co.flyver.utils.flyverMQ.FlyverMQ;
import co.flyver.utils.flyverMQ.FlyverMQMessage;
import co.flyver.utils.flyverMQ.interfaces.FlyverMQConsumer;

/**
 * A {@link android.app.IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class LoggerService implements Runnable, FlyverMQConsumer {
    protected static final String STOP_COMMAND = "stop";
    protected static final String THREAD_NAME = "LoggerThread";

    protected static final int THREAD_COUNT = 3;

    protected int iThreadNumber = 0;

    protected FileLogger m_flLogger;
    protected String m_sFilename = null;
    protected BlockingQueue m_bqQueue;
    protected BlockingQueue m_bqFileQueue;
    protected Thread m_thThreads[];
    protected Thread m_thFileThread;

    //protected HttpClient m_hcClient;
    protected String eventURL, eventMethod, eventToken;

    protected FlyverMQ m_mqQueue;

    public LoggerService(Context context, FlyverMQ queue) {
        this(context);

        m_mqQueue = queue;
    }

    public LoggerService(Context context) {
        m_bqQueue = new LinkedBlockingQueue(100);
        m_bqFileQueue = new LinkedBlockingQueue(100);
        InitProperties(context);

        m_mqQueue = null;

        m_thFileThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runFileThread();
            }
        });

        m_thFileThread.start();
    }

    public void run() {
        int myThreadNumber = iThreadNumber++;

        HttpClient hcClient = CreateKeepAliveHttpClient();

        try {
            Log.i("LoggerService", "Thread " + myThreadNumber + " started.");

            while (true) {
                //Log.d("LoggerService", "" + myThreadNumber + ": Waiting for an item from the queue.");
                Object obj = m_bqQueue.take();

                if (obj.getClass() == SimpleEvent.class) {
                    //Log.d("LoggerService", "" + myThreadNumber + ": Processing a SimpleEvent.");
                    HandleEventLog(hcClient, (SimpleEvent) obj);
                }
                else if (obj.getClass() == CommandEvent.class) {
                    Log.d("LoggerService", "" + myThreadNumber + ": Processing a CommandEvent.");
                    if (((CommandEvent) obj).EventData.equals(STOP_COMMAND)) {
                        throw new InterruptedException();
                    }
                else
                    Log.d("LoggerService", "" + myThreadNumber + ": Unknown Object received: " + obj.toString());
                }
            }
        } catch(InterruptedException ie) {
            Log.w("LoggerService", "" + myThreadNumber + ": INTERRUPTED!");
        } catch(Exception ex) {
            Log.w("LoggerService", "" + myThreadNumber + ": Generic ERROR: " + ex.toString());
            ex.printStackTrace();
        } finally {
            if (hcClient != null) {
                hcClient = null;
            }
        }
    }

    public void runFileThread() {
        try {
            Log.i("LoggerService", "Thread FileThread started.");

            while (true) {
                //Log.d("LoggerService", "" + myThreadNumber + ": Waiting for an item from the queue.");
                Object obj = m_bqFileQueue.take();

                if (obj.getClass() == SimpleEvent.class) {
                    //Log.d("LoggerService", "FileThread: Processing a SimpleEvent.");
                    m_flLogger.WriteLogEntry((SimpleEvent) obj);
                }
                else if (obj.getClass() == CommandEvent.class) {
                    Log.d("LoggerService", "FileThread: Processing a CommandEvent.");
                    if (((CommandEvent) obj).EventData.equals(STOP_COMMAND)) {
                        throw new InterruptedException();
                    }
                    else
                        Log.d("LoggerService", "FileThread: Unknown Object received: " + obj.toString());
                }
            }
        } catch(InterruptedException ie) {
            Log.w("LoggerService", "FileThread: INTERRUPTED!");
        } catch(Exception ex) {
            Log.w("LoggerService", "FileThread: Generic ERROR: " + ex.toString());
            ex.printStackTrace();
        } finally {
        }
    }

    public boolean Start() {
        m_thThreads = new Thread[3];

        for (int i = 0; i < LoggerService.THREAD_COUNT; i++)
        {
            m_thThreads[i] = new Thread(this, THREAD_NAME + i);
            m_thThreads[i].start();
        }

        if (m_mqQueue != null)
            m_mqQueue.registerConsumer(this, "sensors.raw");

        return true;
    }

    public boolean Stop() {
        return this.Stop(false);
    }

    public boolean Stop(boolean bImmediately) {
        if (m_thThreads == null)
            return true;

        for (int i = 0; i < LoggerService.THREAD_COUNT; i++) {
            if (m_thThreads[i].getState() == Thread.State.TERMINATED) {
                continue;
            }

            if (bImmediately) {
                m_thThreads[i].interrupt();
                continue;
            }

            if (m_thThreads[i].getState() == Thread.State.RUNNABLE && m_bqQueue != null) {
                m_bqQueue.add(CommandEvent.CreateStopCommand());
            }
        }

        if (m_thFileThread.getState() == Thread.State.RUNNABLE) {
            if (bImmediately)
                m_thFileThread.interrupt();
            else if (m_bqFileQueue != null)
                m_bqFileQueue.add(CommandEvent.CreateStopCommand());
        }

        m_thFileThread = null;
        m_thThreads = null;

        return true;
    }

    public boolean LocalReadFromStart()
    {
        return m_flLogger.ReadFromStart();
    }

    public SimpleEvent LocalReadLogEntry()
    {
        return m_flLogger.ReadLogEntry();
    }

    public boolean LocalGoToEnd()
    {
        return m_flLogger.GoToEnd();
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

            Log.i("LoggerService", "Internal Storage Path: " + sharedPref.getString(IntCfg.internal_storage_path, null));

            boolean bInternalStorage = new Boolean(sharedPref.getString(IntCfg.save_internally, "true")).booleanValue();
            if (!isExternalStorageWritable() && !bInternalStorage) {
                bInternalStorage = true;
            }

            if (bInternalStorage)
                m_sFilename = sharedPref.getString(IntCfg.internal_storage_path, null) + File.separator + sharedPref.getString(IntCfg.local_filename, null);
            else
                m_sFilename = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + sharedPref.getString(IntCfg.local_filename, null);

            Log.i("LoggerService", "Filename: " + m_sFilename);

            m_flLogger = new FileLogger(m_sFilename);

            eventURL = sharedPref.getString(IntCfg.KEY_LOG_URL, "").toLowerCase();
            eventToken = sharedPref.getString(IntCfg.KEY_ACCESS_TOKEN, "");
            eventMethod = sharedPref.getString(IntCfg.KEY_LOG_METHOD, "").toLowerCase();

            Log.i("LoggerService", "eventURL: " + eventURL + ", eventToken: " + eventToken + ", eventMethod: " + eventMethod);
        } catch(Exception ex) {
            Log.w("LoggerService", "ERROR!");
            ex.printStackTrace();
        }
    }

    protected void InitHttpClient() {
        //m_hcClient = CreateKeepAliveHttpClient();
    }

    protected HttpClient CreateKeepAliveHttpClient() {
        HttpClient hcClient = new DefaultHttpClient();
        ClientConnectionManager mgr = hcClient.getConnectionManager();
        HttpParams hp = hcClient.getParams();
        HttpConnectionParams.setConnectionTimeout(hp, 7500);
        HttpConnectionParams.setSoTimeout(hp, 7500);
        hcClient = new DefaultHttpClient(
                new ThreadSafeClientConnManager(hp,mgr.getSchemeRegistry()), hp);

        return hcClient;
    }

    protected String GetResponseText(HttpResponse response) {
        BufferedReader in = null;
        StringBuffer sb = null;

        try {
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
            Log.w("LoggerService", ex.getMessage());
            ex.printStackTrace();
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

    protected boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public void Dispose() {
        if (m_flLogger != null) {
            m_flLogger.Dispose();
            m_flLogger = null;
        }

        if (m_mqQueue != null) {
            //TODO: need to implement unregisterConsumer
            //m_mqQueue.unregisterConsumer(this, "sensors.raw");
        }

        //if (m_hcClient != null) {
        //    m_hcClient = null;
        //}

        this.Stop();
    }

    public void LogData(SimpleEvent sEvent) {
        //Log.d("LoggerService", "LogData: " + sEvent.getEventType());

        try{
            if (m_bqQueue.remainingCapacity() > 0)
                m_bqQueue.add(sEvent);
            else
                Log.d("LoggerService", "Queue FULL, Event DROPPED!");

            if (m_bqFileQueue.remainingCapacity() > 0)
                m_bqFileQueue.add(sEvent);
            else
                Log.d("LoggerService", "FILE Queue FULL, Event DROPPED!");
        } catch (Exception ex) {
            Log.d("LoggerService", "LogData error: " + ex.toString());
        } finally {

        }
    }

    public void LogData(String eType, String eTags, String eData) {
        LogData(new SimpleEvent(eType, eTags, eData));
    }

    protected void HandleEventLog(HttpClient hcClient, SimpleEvent se) {
        //Log.i("LoggerService", "HandleEventLog: eURL: " + eventURL + ", eMethod: " + eventMethod + ", eventType: " + se.getEventType());

        //try {
        //    m_flLogger.WriteLogEntry(se);
        //} catch(Exception ex) {
        //    Log.w("LoggerService", "Generic error!");
        //    ex.printStackTrace();
        //}

        if (eventMethod.equals("get"))
            httpGET(hcClient, eventURL, eventToken, se);
        else if (eventMethod.equals("post"))
            httpPOST(hcClient, eventURL, eventToken, se);
    }

    protected void HandleEventLog(HttpClient hcClient, String eURL, String eMethod, String eToken, String eType, String eTags, String eData) {
        HandleEventLog(hcClient, new SimpleEvent(eType, eTags, eData));
    }

    protected String httpGET(HttpClient hcClient, String eURL, String eToken, SimpleEvent se) {
        //Log.i("LoggerService", "httpGET " + eURL);
        String sb = null;

        try {
            HttpGet request = new HttpGet();
            request.setURI(new URI(eURL + eToken + "/"
                    + URLEncoder.encode(se.getEventType(), "UTF-8") + "/"
                    + URLEncoder.encode(se.getEventTags(), "UTF-8") + "/"
                    + URLEncoder.encode(se.getEventData(), "UTF-8") + "/"
                    + URLEncoder.encode("" + se.getEventTimeStamp(), "UTF-8")));
            request.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);
            HttpResponse response = hcClient.execute(request);
            sb = GetResponseText(response);
        } catch (Exception ex) {
            Log.w("LoggerService", ex.getMessage());
            ex.printStackTrace();
            sb = null;
        } finally {
        }

        return sb;
    }

    protected String httpGET(HttpClient hcClient, String eURL, String eToken, String eType, String eTags, String eData) {
        return httpGET(hcClient, eURL, eToken, new SimpleEvent(eType, eTags, eData));
    }

    protected String httpPOST(HttpClient hcClient, String eURL, String eToken, SimpleEvent se) {
        //Log.d("LoggerService", "STARTED httpPOST");

        String sb = null;
        // Create a new HttpClient and Post Header
        HttpPost request = new HttpPost(eURL);
        request.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);

        try {
            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(4);
            nameValuePairs.add(new BasicNameValuePair("token", eToken));
            nameValuePairs.add(new BasicNameValuePair("entrytype", se.getEventType()));
            nameValuePairs.add(new BasicNameValuePair("entrytags", se.getEventTags()));
            nameValuePairs.add(new BasicNameValuePair("entrydata", se.getEventData()));
            nameValuePairs.add(new BasicNameValuePair("entrytimestamp", "" + se.getEventTimeStamp()));

            request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            request.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_KEEP_ALIVE);

            // Execute HTTP Post Request
            HttpResponse response = hcClient.execute(request);
            sb = GetResponseText(response);
        } catch(Exception ex)
        {
            Log.w("LoggerService", ex.toString());
            ex.printStackTrace();
            sb = null;
        }
        finally {
        }

        Log.d("LoggerService", "ENDED httpPOST " + sb);

        return sb;
    }

    protected String httpPOST(HttpClient hcClient, String eURL, String eToken, String eType, String eTags, String eData) {
        return httpPOST(hcClient, eURL, eToken, new SimpleEvent(eType, eTags, eData));
    }

    protected int count = 0;

    @Override
    public void dataReceived(FlyverMQMessage message) {
        if (count++ > 5) {
            LogData(new SimpleEvent(message.topic, tagsFromMessage(message), message.data.toString(), message.creationTime));
            count = 0;
        }
    }

    @Override
    public void unregistered() {
        Log.d("LoggerService", "has been unregistered from the Message Queue.");
    }

    @Override
    public void paused() {

    }

    @Override
    public void resumed() {

    }

    protected String tagsFromMessage(FlyverMQMessage message) {
        JSONObject json = new JSONObject();
        String sRet = "";

        try {
            json.put("messageid", message.messageId);
            json.put("priority", message.priority);
            json.put("ttl", message.ttl);
            sRet = json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            sRet = "";
        }

        return sRet;
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