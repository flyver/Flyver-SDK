package co.flyver.Client;

import android.util.Log;

/**
 * Created by flyver on 11/27/14.
 */
public class HeartbeatMonitor implements Runnable {
    private static final String HEARTBEAT = "HeartbeatMonitor";
    private static boolean heartbeatReceived = false;
    private static int heartbeatsMissed = 0;
    private Runnable onHeartbeatMissed;

    public HeartbeatMonitor setOnHeartbeatMissedHook(Runnable onHeartbeatMissed) {
        this.onHeartbeatMissed = onHeartbeatMissed;
        return this;
    }

    public HeartbeatMonitor() {
    }
    public void heartbeatReceived() {
        heartbeatReceived = true;
        heartbeatsMissed = 0;
    }

    @Override
    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            if (!heartbeatReceived) {
                heartbeatsMissed++;
                Log.w(HEARTBEAT, "Heartbeats missed: " + heartbeatsMissed);
            } else {
                heartbeatReceived = false;
            }
            if (heartbeatsMissed > 5) {
                heartbeatsMissed = 0;
                onHeartbeatMissed.run();
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
