package co.flyver.androidrc.Server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.video.VideoQuality;

/**
 * Created by flyver on 11/5/14.
 */
public class VideoStreamProvider  extends CameraProvider implements Session.Callback, SurfaceHolder.Callback {

    private static final String VIDEOSTREAM = "VIDEOSTREAM";

    private Session mSession;

    public VideoStreamProvider(Context context, Camera camera) {
        super(context, camera);
        Log.d(VIDEOSTREAM, "Created");
        super.init();
    }

    public VideoStreamProvider(Context context) {
        super(context);
        super.init();
    }

    public void setCallback(Runnable callback) {
        super.setCallback(callback);
    }

    public void init() {

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this.getContext()).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(54321));
        editor.apply();

        mSession = SessionBuilder.getInstance()
                .setCallback(this)
                .setSurfaceView(mView)
                .setPreviewOrientation(90)
                .setContext(this.getContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setAudioQuality(new AudioQuality(16000, 32000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(800,480,10,500000))
                .build();

        mView.getHolder().addCallback(this);

        this.getContext().startService(new Intent(this.getContext(),RtspServer.class));

    }

    @Override
    public void onBitrateUpdate(long bitrate) {

        Log.d(VIDEOSTREAM, "Bitrate updated " + bitrate);
    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {

    }

    @Override
    public void onPreviewStarted() {

        Log.d(VIDEOSTREAM, "Preview started");
    }

    @Override
    public void onSessionConfigured() {

        Log.d(VIDEOSTREAM, "Session configured");
        mSession.start();
    }

    @Override
    public void onSessionStarted() {

        Log.d(VIDEOSTREAM, "Session started");
    }

    @Override
    public void onSessionStopped() {
        Log.d(VIDEOSTREAM, "Session stopped");

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSession.stop();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
//        mSession.startPreview();
    }
}
