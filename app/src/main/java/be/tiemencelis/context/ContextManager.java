package be.tiemencelis.context;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import org.apache.commons.net.ntp.TimeStamp;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.kuleuven.cs.priman.manager.ConnectionManager;

/**
 * Created by Tiemen on 18-5-2015.
 * Provides interface for requesting all kinds of contextinformation
 * Also stores cached contextinformation for improved speed and battery consumption
 */

public class ContextManager extends BroadcastReceiver {
    private static final int FIVE_MINUTES = 1000 * 60 * 5;

    private static Context context;
    private static LocationManager locationManager;
    private static Location lastLocation = null;

    private static WifiManager wifiManager;
    private static WifiInfo currentWifiConnection = null;

    private static BluetoothManager bluetoothManager;
    private static Set<BluetoothDevice> connectedBluetoothDevices;

    private static Map<Long, String> lastNFCTags;

    private static long lastNtpTime = 0;


    /**
     * Initialise all context types and listeners
     * @param context
     */
    public static void init(Context context) {
        ContextManager.context = context;

        /*Request last known and best location as init value*/
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (isBetterLocation(gpsLocation, networkLocation)) {
            lastLocation = gpsLocation;
        }
        else  {
            lastLocation = networkLocation;
        }

        /*Start listener for location updates*/
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000*30, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000*60, 0, locationListener);

        /*Request current wifi information*/
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            currentWifiConnection = wifiManager.getConnectionInfo();
            System.out.println("Wifi: " + currentWifiConnection.getSSID());
        }

        /*Request current bluetooth information*/
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        connectedBluetoothDevices = bluetoothManager.getAdapter().getBondedDevices();
        System.out.println("Connected bluetooth devices: ");
        for (BluetoothDevice dev : connectedBluetoothDevices) {
            System.out.println("Device: " + dev.getName());
        }

        lastNFCTags = new HashMap<Long, String>();

        /*Request ntp time as init value*/
        try {
            lastNtpTime = getNtpTime(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Get location
     * @return best and most recent location
     */
    public static Location getLocation() {
        return lastLocation;
    }


    /**
     * Get the connected cell tower id
     * @return cell id as int
     */
    public static int getCellId() {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        GsmCellLocation cellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
        int cid = cellLocation.getCid();
        System.out.println("GSM cid: " + cid);

        return cid;
    }


    /**
     * Get the current timestamp based on the machine time
     * @return timestamp in milliseconds (Unix format)
     */
    public static long getSystemTime() {
        return System.currentTimeMillis();
    }


    /*TODO threaded, meerdere ip's proberen?, volledige classe threaded en maar 1 keer! (oncreate komt altijd opnieuw bij scherm wijziging)*/
    /**
     * Get a ntp timestamp which is not older than the specified age (based on system time)
     * @param maxAge maximum age in milliseconds!
     * @return timestamp in milliseconds (Unix format)
     * @throws Exception when ntp request failed
     */
    public static long getNtpTime(long maxAge) throws Exception {
        long ntpTime;

        if (lastNtpTime > System.currentTimeMillis() - maxAge) {
            return lastNtpTime;
        }

        try {
            lastNtpTime = Time.getNtpTime();
            System.out.println("System time: " + System.currentTimeMillis());
            System.out.println("NTP time: " + lastNtpTime);
        } catch (Exception e) {
            System.out.println("Error fetching NTP time from " + Time.serverName);
            throw e;
        }

        return lastNtpTime;
    }


    /**
     * Get the connected wifi connection info
     * @return WifiInfo is current connection
     */
    public WifiInfo getCurrentWifiConnection() {
        return currentWifiConnection;
    }


    /**
     * Get a list of all configured wifi networks of the device
     * @return List of WifiConfiguration objects
     */
    public List<WifiConfiguration> getConfiguredWifi() {
        return wifiManager.getConfiguredNetworks();
    }


    /**
     * Get a set of all the connected bluetooth devices.
     * @return Set of BluetoothDevice objects
     */
    public Set<BluetoothDevice> getConnectedBluetoothDevices() {
        return connectedBluetoothDevices;
    }


    public static void addLastNFCTags(long time, String tag) {
        System.out.println("Time: " + time + " Tag: " + tag);
        ContextManager.lastNFCTags.put(time, tag);
    }

    /**
     * Listener for changes of the wifi connection. SHOULD NOT BE CALLED!
     * @param context
     * @param intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            System.out.println("BROADCAST RECEIVER: Wifi changed");
            currentWifiConnection = wifiManager.getConnectionInfo();
        }
        else if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
            System.out.println("BROADCAST RECEIVER: Bluetooth changed");
            connectedBluetoothDevices = bluetoothManager.getAdapter().getBondedDevices();
        }
    }


    /**
     * Listener for location updates
     */
    private static LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, lastLocation)) {
                lastLocation = location;
                System.out.println("New location (better): " + location.toString());
            }
            else
                System.out.println("New location (worse): " + location.toString());
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };


    /**
     * Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     * Source: Google
     */
    private static boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }
        if (location == null) {
            // New location is null, keep the old one
            return false;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > FIVE_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -FIVE_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than five minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than five minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same
     *  Source: Google
     * */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }
}
