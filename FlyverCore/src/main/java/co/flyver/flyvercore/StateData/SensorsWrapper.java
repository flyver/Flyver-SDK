package co.flyver.flyvercore.StateData;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.Arrays;

import co.flyver.utils.flyverMQ.FlyverMQMessage;
import co.flyver.utils.flyverMQ.FlyverMQProducer;
import co.flyver.utils.flyverMQ.exceptions.ProducerAlreadyRegisteredException;

/**
 * Created by Petar Petrov on 12/4/14.
 */
public class SensorsWrapper extends FlyverMQProducer implements SensorEventListener {

    /* Constants */
    public static final float ALTITUDE_SMOOTHING = 0.95f;
    public static final String TOPIC = "sensors.raw";

    @Override
    public void registered() {

    }

    @Override
    public void unregistered() {

    }

    public interface RotationVectorChanged {
        public void onRotationChanged(float[] orientationMatrix);
    }

    public interface LocationChanged {
        public void onLocationChanged(float xPos, float yPos, float xSpeed, float ySpeed);
    }

    private float[] rotationVec = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] yawPitchRollVec = new float[3];

    private long lastTime = 0;
    Sensor rotationSensor;
    Sensor pressureSensor;
    SensorManager sensorManager;
    ArrayList<RotationVectorChanged> rotationListeners = new ArrayList<>();
    ArrayList<LocationChanged> locationListeners = new ArrayList<>();
    boolean paused = false;

    public long getLastTime() {
        return lastTime;
    }

    public SensorsWrapper registerRotationVectorListener(RotationVectorChanged listener) {
        rotationListeners.add(listener);
        return this;
    }

    public SensorsWrapper registerLocationChangeListener(LocationChanged listener) {
        locationListeners.add(listener);
        return this;
    }

    public SensorsWrapper(Activity activity) {
        super(TOPIC);
        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        try {
            register(false);
        } catch (ProducerAlreadyRegisteredException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == rotationSensor) {
            // Get the time and the rotation vector.
            lastTime = event.timestamp;
            System.arraycopy(event.values, 0, rotationVec, 0, 3);

            // Convert the to "yaw, pitch, roll".
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVec);
            SensorManager.getOrientation(rotationMatrix, yawPitchRollVec);
            FlyverMQMessage message = new FlyverMQMessage.MessageBuilder().setCreationTime(System.nanoTime()).
                    setMessageId(13532).
                    setTopic(TOPIC).
                    setPriority((short) 1).
                    setTtl(12341).
                    setData(Arrays.toString(yawPitchRollVec)).
                    build();

            addMessage(message);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onPause() {
        if (paused) {
            return;
        }
        paused = true;
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onResume() {

    }

    public void start() {
//        if (!paused) {
//            return;
//        }
        paused = false;
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
}
