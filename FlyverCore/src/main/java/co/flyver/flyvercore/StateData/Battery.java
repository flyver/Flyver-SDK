package co.flyver.flyvercore.StateData;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import co.flyver.flyvercore.MicroControllers.MicroController;

/**
 * Stores information about the battery
 * TODO: Add APIs for smart data such as discharge rate
 */
public class Battery {

    public class CapacityQueue<E> extends LinkedList<E> {
        private int limit;

        public CapacityQueue(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean add(E e) {
            boolean added = super.add(e);
            while(added && size() > limit) {
                super.remove();
            }
            return added;
        }
    }

    public interface onStatusChanged {
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
    /* End of */

    /* Variables */
    private int lastStatusPercentage;

    /** This is default voltage divider coefficient for 3 cell LiPo battery
     *  The coefficient is real value of 2w resistors with calculated div coefficient of 5.81
     *  It could be reproduced with voltage divider with R1 = 5.5 KOhms and R2 = 32 KOhms
     */
    private float dividerCoefficient = 6.94f;

    /* End of */

    /* Objects and refs */

    private Timer timer = new Timer();
    private BatteryCells batteryCells;
    MicroController microController;
    private Queue<Float> queue = new CapacityQueue<>(15);
    private onStatusChanged statusChangeCb;
    private final Semaphore semaphore = new Semaphore(0);

    /* End of */

    public void setStatusChangeCb(onStatusChanged statusChangeCb) {
        this.statusChangeCb = statusChangeCb;
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
    /**
     * @return battery status in %
     */
    public int getBatteryStatus() {
        int batteryStatus;
        float maxVoltage = cellMaxVoltage*batteryCells.getValue()/dividerCoefficient;
        float minVoltage = cellMinVoltage*batteryCells.getValue()/dividerCoefficient;
        float batteryVoltage = microController.getBatteryVoltage();
        queue.add(batteryVoltage);
        float medianVoltage = calculateMedianVoltage();

        batteryStatus = (int) (((medianVoltage - minVoltage) / (maxVoltage - minVoltage))*100f);
        if(batteryStatus != lastStatusPercentage) {
            if (statusChangeCb != null) {
                statusChangeCb.onChange(batteryStatus);
            }
        }
        lastStatusPercentage = batteryStatus;
        Log.d("Battery", String.format("Total voltage: %f Voltage: %f maxVoltage: %f, minVoltage: %f percentage: %d%%", getBatteryVoltage(), medianVoltage, maxVoltage, minVoltage, batteryStatus));
        return batteryStatus;
    }
    public float getBatteryVoltage(){
        return calculateMedianVoltage() * batteryCells.getValue();
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

    public void setDividerCoefficient(float dividerCoefficient) {
        this.dividerCoefficient = dividerCoefficient;
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

    public void pause() {
        timer.cancel();
        timer.purge();
        timer = new Timer();
    }

    public void resume() {
        queue = new CapacityQueue<>(15);
        start();
    }

    private void start() {
        //poll the IOIOController every second for battery status update
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                getBatteryStatus();
            }
        }, 1, 1000);
    }
}
