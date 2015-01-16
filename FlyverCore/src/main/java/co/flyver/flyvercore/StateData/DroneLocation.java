package co.flyver.flyvercore.StateData;

import android.location.Location;

/**
 * Created by Tihomir Nedev on 15-1-14.
 */

public class DroneLocation {

    /* Constants */
    private static final float PI = 3.14159265359f;
    private static final float RAD_TO_DEG = 180.0f / PI;
    private static final double DEG_TO_RAD = PI / 180.0;
    private static final float ALTITUDE_SMOOTHING = 0.95f;
    private static final double EARTH_RADIUS = 6371000; // [m].
    private static final int MIN_ACCURACY = 20;
    private static final int MED_ACCURACY = 10;
    private static final int HIGH_ACCURACY = 6;
    private static final int TIMOUT_TIME = 5*1000; // Timeout time for receiving data with lower quality than MIN_ACCURACY. If this time passes, the location services is set to be unreliable.
        /* End of*/

    private Location location;
    private long lastUpdate;

    public void setLocation(Location location){
        this.location = location;
    }

    public Location getLocation(){
        return location;
    }

    public double getGpsAltitude() {
        return location.getAltitude();
    }


    public float getAccuracy() {
        return location.getAccuracy();
    }

    public int getSatellitesCount() {
        return (Integer) location.getExtras().get("satellites");
    }

    public double getLatitude() {

        return location.getLatitude();
    }

    public float getxSpeed() {
        return location.getSpeed() * (float) Math.cos(location.getBearing());
    }

    public float getySpeed() {
        return location.getSpeed() * (float) Math.sin(location.getBearing());
    }


    public float getxPos() {
        return (float) (EARTH_RADIUS * location.getLongitude() * DEG_TO_RAD);
    }

    public float getyPos() {
        return (float) (EARTH_RADIUS * location.getLatitude() * DEG_TO_RAD);
    }

    public double getLongitude() {

        return location.getLongitude();
    }

    public Quality getQuality(){

        float accuracy = location.getAccuracy();
        long currentTime = location.getTime();

        if(accuracy<MIN_ACCURACY){
            lastUpdate = location.getTime();
        }

        if(accuracy<=HIGH_ACCURACY && currentTime - lastUpdate < TIMOUT_TIME){
            return Quality.HIGH;
        }
        else if(accuracy<=MED_ACCURACY && currentTime - lastUpdate  < TIMOUT_TIME){
            return Quality.MEDIUM;
        }
        else if(accuracy>=MIN_ACCURACY && currentTime - lastUpdate  < TIMOUT_TIME){
            return Quality.LOW;
        }
        else {
            return  Quality.UNRELIABLE;
        }
    }

    public String toString (){
        return
                "x: " +
                Float.toString(this.getxPos())+
                ", y: " +
                Float.toString(this.getyPos())+
                ", alt: " +
                Double.toString(this.getGpsAltitude())+
                ", accuracy: " +
                Float.toString(this.getAccuracy()) +
                ", quality: " +
                this.getQuality();
    }

    public enum Quality{
        LOW,
        MEDIUM,
        HIGH,
        UNRELIABLE;
    }
}
