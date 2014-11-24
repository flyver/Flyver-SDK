package co.flyver.dataloggerlib;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

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
        //absPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

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
        }

        return bRet;
    }

    public void Dispose() {
        try
        {
            m_rafLogFile.close();
        } catch(IOException ioe) {
        }
    }
}
