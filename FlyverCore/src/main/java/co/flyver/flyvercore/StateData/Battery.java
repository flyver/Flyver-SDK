package co.flyver.flyvercore.StateData;

import co.flyver.flyvercore.MicroControllers.MicroController;

/**
 * Stores information about the battery
 * TODO: Add APIs for smart data such as discharge rate
 */
public class Battery {

    /* Constants */
    final float cellMaxVoltage = 4.2f;
    final float cellMinVoltage = 3.00f;
    /* End of */

    /* Variables */
    private int batteryCells;
    private float batteryVoltage;
    MicroController microController;

    /** This is default voltage divider coefficient for 3 cell Lipo battery
     *   The coefficient is real value of 2w resisors with calculated div coefficient of 5.81
     *  It could be reproduced with voltage divider with R1 = 5.5 KOhms and R2 = 32 KOhms
     */
    private float dividerCoefficient = 6.94f;

    /* End of */


    public Battery (MicroController microController, int batteryCells){
        this.batteryCells = batteryCells;
        this.microController = microController;
    }

    public Battery(MicroController microController, int cells, float dividerCoefficient){
        this.dividerCoefficient = dividerCoefficient;
        this.microController = microController;
    }
    /**
     * @return battery status in %
     */
    public int getBatteryStatus(){
        int batteryStatus;
        float maxVoltage = cellMaxVoltage*batteryCells/dividerCoefficient;
        float minVoltage = cellMinVoltage*batteryCells/dividerCoefficient;
        //float batteryVoltage = microController.getBatteryVoltage();

       // batteryStatus = (int) (((batteryVoltage - minVoltage) / (maxVoltage - minVoltage))*100f);
       // TODO: Battery Status
        return -1 ;
    }
    public float getBatteryVoltage(){
        // TODO: Get the battery votlage
        return -1f;
    }
    public int getBatteryCells() {
        return batteryCells;
    }

    public void setBatteryCells(byte batteryCells) {

        this.batteryCells = batteryCells;
    }

    public float getDividerCoefficient() {

        return dividerCoefficient;
    }

    public void setDividerCoefficient(float dividerCoefficient) {
        this.dividerCoefficient = dividerCoefficient;
    }

}
