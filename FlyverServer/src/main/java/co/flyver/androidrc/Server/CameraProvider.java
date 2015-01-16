package co.flyver.androidrc.Server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

/**
 * Created by Petar Petrov on 10/24/14.
 */
public class CameraProvider extends SurfaceView implements SurfaceHolder.Callback {
    private final String CAMERA = "CAMERA";

    SurfaceHolder mSurfaceHolder;
    Camera mCamera;
    Runnable mCallback;
    boolean mReady = true;
    net.majorkernelpanic.streaming.gl.SurfaceView mView;
    byte[] mLastPicture;

    public byte[] getLastPicture() {
        return mLastPicture;
    }

    public void setCallback(Runnable callback) {
        this.mCallback = callback;
    }
    public void setView(net.majorkernelpanic.streaming.gl.SurfaceView view) {
        this.mView = view;
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            Log.d(CAMERA, "Surface created");
            mCamera.setPreviewDisplay(holder);
            mCamera.setDisplayOrientation(90);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCamera.stopPreview();

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    private Camera.PictureCallback capturedIt = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if(bitmap==null) {
                Log.d(CAMERA, "Picture not taken!");
            }
            else {
                Log.d(CAMERA, "Picture data length: " + bitmap.getByteCount() / 1024 + "kB");
                Log.d(CAMERA, "Picture data: " + Arrays.toString(data));
                camera.startPreview();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mLastPicture = data;
            if (mCallback != null) {
                mCallback.run();
            }
            mReady = true;
        }
    };

    public void snapIt(){
        if(mReady) {
            mCamera.takePicture(null, null, capturedIt);
            Log.d(CAMERA, "Pic taken");
            mReady = false;
        }
    }

    public void snapIt(Camera camera){
        if(mReady) {
            if(camera != null) {
                mSurfaceHolder.addCallback(this);
                try {
                    camera.setPreviewDisplay(mSurfaceHolder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                camera.setDisplayOrientation(90);
                camera.startPreview();
            } else {
                return;
            }
//            camera.takePicture(null, null, capturedIt);
            Log.d(CAMERA, "Pic taken");
            mReady = false;
        }
    }

    public CameraProvider(Context context, Camera mCamera) {
        super(context);
        this.mCamera = mCamera;
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }

    public CameraProvider(Context context) {
        super(context);
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }

    public void init() {
        Log.d(CAMERA, "Initiated");
//        mCamera.startPreview();
    }

    public static Camera getCameraInstance(){
        Camera camera = null;
        try {
            camera = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            e.printStackTrace();
        }
        return camera; // returns null if mCamera is unavailable
    }
}
