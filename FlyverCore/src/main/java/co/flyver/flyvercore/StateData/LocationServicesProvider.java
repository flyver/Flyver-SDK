package co.flyver.flyvercore.StateData;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import co.flyver.flyvercore.MainControllers.MainController;
import co.flyver.utils.flyverMQ.FlyverMQMessage;
import co.flyver.utils.flyverMQ.FlyverMQProducer;
import co.flyver.utils.flyverMQ.exceptions.ProducerAlreadyRegisteredException;

/**
 * Created by Tihomir Nedev on 15-1-9.
 */
public class LocationServicesProvider extends FlyverMQProducer implements com.google.android.gms.location.LocationListener,GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    /* Constants */
    public static final String TOPIC = "LocationServices";

    private static final long INTERVAL = 100;
    private static final long FASTEST_INTERVAL = 10;
    private static final long ONE_MIN = 1000 * 60;
    private static final long REFRESH_TIME = ONE_MIN * 5;
    private static final float MINIMUM_ACCURACY = 50.0f;

    /* End of */

    private DroneLocation droneLocation;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private Location location;
    private FusedLocationProviderApi fusedLocationProviderApi;
    private Context context;

    public LocationServicesProvider() {
        super(TOPIC);

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        context = MainController.getInstance().getMainActivityRef();

        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        fusedLocationProviderApi = LocationServices.FusedLocationApi;

        droneLocation = new DroneLocation();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }
        try {
            register(false);
        } catch (ProducerAlreadyRegisteredException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void registered() {

    }

    @Override
    public void unregistered() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void onLocationChanged(Location location) {

        droneLocation.setLocation(location);

        FlyverMQMessage message = new FlyverMQMessage.MessageBuilder().setCreationTime(System.nanoTime()).
                setMessageId(13000).
                setTopic(TOPIC).
                setPriority((short) 2).
                setTtl(12341).
                setData(droneLocation).
                build();

        addMessage(message);
    }

    @Override
    public void onConnected(Bundle bundle) {
        fusedLocationProviderApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        fusedLocationProviderApi.removeLocationUpdates(googleApiClient,this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
