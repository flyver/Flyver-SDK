package co.flyver.flyvercore.StateData;

import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import co.flyver.flyvercore.MicroControllers.MicroController;
import co.flyver.utils.CapacityQueue;

/**
 * Stores information about the battery
 * TODO: Add APIs for smart data such as discharge rate
 */
public class Battery {

    public interface StatusChanged {
        public void onChange(int status);
    }

    public enum BatteryCells {
        TWO(2),
        THREE(3),
        FOUR(4),
        FIVE(5),
        SIX(6);

        private int count;
        private BatteryCells(int count) {
            this.count = count;
        }
        public int getValue() {
            return count;
        }
    }
    /* Constants */
    final float cellMaxVoltage = 4.2f;
    final float cellMinVoltage = 3.3f;
    private final int queueCapacity = 20;
    /* End of */

    /* Variables */
    private int lastStatus;
    private boolean batteryNormalized;
    private int values;

    /** This is default voltage divider coefficient for 3 cell LiPo battery
     *  The coefficient is real value of 2w resistors with calculated div coefficient of 5.81
     *  It could be reproduced with voltage divider with R1 = 5.5 KOhms and R2 = 32 KOhms
     */
    private float dividerCoefficient = 6.94f;

    /* End of */

    /* Objects and refs */

    private Timer timer;
    private BatteryCells batteryCells;
    MicroController microController;
    private CapacityQueue<Float> queue = new CapacityQueue<>(queueCapacity);
    private CapacityQueue<Integer> percentages = new CapacityQueue<>(queueCapacity);
    private StatusChanged statusChangeCb;
    private Runnable batteryCriticalCallback;
    
    /* End of */

    public Battery setStatusChangeCb(StatusChanged statusChangeCb) {
        this.statusChangeCb = statusChangeCb;
        return this;
    }

    public Battery setBatteryCriticalCallback(Runnable cb) {
        this.batteryCriticalCallback = cb;
        return this;
    }

    public Battery (MicroController microController, BatteryCells cells){
        this.batteryCells = cells;
        this.microController = microController;
        start();
    }

    public Battery(MicroController microController, BatteryCells cells, float dividerCoefficient){
        this.dividerCoefficient = dividerCoefficient;
        this.batteryCells = cells;
        this.microController = microController;
        start();
    }
    public void updateBatteryStatus() {
        int batteryStatus;
        float maxVoltage = cellMaxVoltage*batteryCells.getValue()/dividerCoefficient;
        float minVoltage = cellMinVoltage*batteryCells.getValue()/dividerCoefficient;
        //poll IOIO for new battery voltage. Add it to a queue for further calculations
        queue.add(microController.getBatteryVoltage());

        //wait until enough values has been collected for more accurate calculations
        values++;
        if(values > (queueCapacity + queueCapacity / 3)) {
            batteryNormalized = true;
        }

        if(!batteryNormalized) {
            return;
        }

        //calculate the median of the last readings to increase accuracy
        float medianVoltage = calculateMedianVoltage();

        percentages.add((int)(((medianVoltage - minVoltage) / (maxVoltage - minVoltage))*100f));
        batteryStatus = calculateMedianPercentage();

        if(batteryStatus != lastStatus) {
            if (statusChangeCb != null) {
                statusChangeCb.onChange(batteryStatus);
            }
        }

        if(getSingleCellVoltage() < cellMinVoltage) {
            if (batteryCriticalCallback != null) {
                batteryCriticalCallback.run();
            }
        }
        lastStatus = batteryStatus;
//        Log.d("Battery", String.format("Total voltage: %f Single cell Voltage: %f Voltage: %f percentage: %d%%", getBatteryVoltage(), getSingleCellVoltage(), medianVoltage, batteryStatus));
    }
    public float getBatteryVoltage(){
        return calculateMedianVoltage() * dividerCoefficient;
    }

    private float getSingleCellVoltage() {
        return getBatteryVoltage() / batteryCells.getValue();
    }
    public int getBatteryCells() {
        return batteryCells.getValue();
    }

    public void setBatteryCells(BatteryCells batteryCells) {
        this.batteryCells = batteryCells;
    }

    public float getDividerCoefficient() {
        return dividerCoefficient;
    }

    public int getBatteryStatus() {
        return lastStatus;
    }
    public void setDividerCoefficient(float dividerCoefficient) {
        this.dividerCoefficient = dividerCoefficient;
    }
    public Battery setMicroController(MicroController microController) {
        this.microController = microController;
        pause();
        resume();
        return this;
    }

    private float calculateMedianVoltage() {
        float median = 0;
        int iterations = 0;
        for (Float f : queue) {
            median += f;
            iterations++;
        }
        return median / iterations;
    }

    private int calculateMedianPercentage() {
        int median = 0;
        int iterations = 0;
        for(Integer i : percentages) {
            median += i;
            iterations++;
        }
        return median / iterations;
    }

    public void pause() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = null;
    }

    public void resume() {
        queue = new CapacityQueue<>(15);
        start();
    }

    private void start() {
        if (microController == null) {
            return;
        }
        if(timer != null ) {
            return;
        }

        timer = new Timer();
        //poll the IOIOController every second for battery status update
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateBatteryStatus();
            }
        }, 1, 500);
    }
}
