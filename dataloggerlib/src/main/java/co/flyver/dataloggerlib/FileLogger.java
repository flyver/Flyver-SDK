package co.flyver.dataloggerlib;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * Created by Valentin Ivanov on 26.10.2014 г..
 */

public class FileLogger {
    protected RandomAccessFile m_rafLogFile;
    protected String m_sFilename = "FlyverLogFile.txt";

    public FileLogger(String sFilename)
    {
        m_sFilename = sFilename;
        m_rafLogFile = null;

        initLogFile();
    }

    public boolean initLogFile() {
        File absPath;
        File logFile = null;

        logFile = new File(m_sFilename);

        if (logFile.isDirectory())
            logFile.delete();

        try {
            if (logFile.getParentFile().mkdirs() || logFile.getParentFile().isDirectory()) {
                if (!logFile.exists())
                    logFile.createNewFile();

                m_rafLogFile = new RandomAccessFile(logFile, "rw");
                m_rafLogFile.seek(m_rafLogFile.length());
            }
        }catch(IOException ioe) {
            m_rafLogFile = null;
        }catch(Exception e) {
            m_rafLogFile = null;
        }

        return true;
    }

    public boolean ReadFromStart()
    {
        try
        {
            m_rafLogFile.seek(0);
        }catch(IOException ioe) {
            Log.w("FileLogger", "ReadFromStart IO Error.");
            return false;
        }

        return true;
    }

    public boolean GoToEnd()
    {
        try
        {
            m_rafLogFile.seek(m_rafLogFile.length());
        }catch(IOException ioe) {
            Log.w("FileLogger", "ReadFromStart IO Error.");
            return false;
        }

        return true;
    }

    public SimpleEvent ReadLogEntry()
    {
        byte[] entryLen = new byte[10], seArray = new byte[4096];
        String sLen = "";
        int len = 0;

        try {
            // Assuming that either ReadFromStart or a previous read left us right at the beginning of the first/next entry
            m_rafLogFile.read(entryLen, 0, entryLen.length);
            sLen = new String(entryLen, 0, 8);
            len = Integer.parseInt(sLen);
            m_rafLogFile.read(seArray, 0, len);
            seArray[len] = 0;
            sLen = new String(seArray, 0, len);
            //Log.i("FileLogger", "ReadLogEntry Encoded string is: " + sLen);

            return new SimpleEvent(sLen);
        } catch(IOException ioe) {
            Log.w("FileLogger", "ReadLogEntry IO Error.");
            ioe.printStackTrace();
        } catch(Exception ex) {
            Log.w("FileLogger", "ReadLogEntry Generic error.");
            ex.printStackTrace();
        }

        return null;
    }

    public boolean WriteLogEntry(String evType, String evTags, String evData) {
        return WriteLogEntry(new SimpleEvent(evType, evTags, evData));
    }

    public boolean WriteLogEntry(SimpleEvent sEvent) {
        boolean bRet = false;
        String sEncoded = sEvent.toString();
        byte[] arr = sEncoded.getBytes();

        try {
            m_rafLogFile.seek(m_rafLogFile.length());
            m_rafLogFile.write(String.format("%08d\r\n", arr.length).getBytes());
            m_rafLogFile.write(arr);

            bRet = true;
        } catch(IOException ioe) {
            bRet = false;
            Log.w("FileLogger", "IO Error. Closing the random access file.");
            ioe.printStackTrace();
        } catch(Exception ex) {
            bRet = false;
            Log.w("FileLogger", "Generic error writing to the random access file.");
            ex.printStackTrace();
        }

        return bRet;
    }

    public void Dispose() {
        try
        {
            m_rafLogFile.close();
        } catch(IOException ioe) {
            Log.w("FileLogger", "IO Error closing the random access file.");
            ioe.printStackTrace();
        } catch(Exception ex) {
            Log.w("FileLogger", "Generic error closing the random access file.");
            ex.printStackTrace();
        }
    }
}
