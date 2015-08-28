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
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import com.ibm.zurich.idmx.dm.DomNym;
import com.ibm.zurich.idmx.dm.MasterSecret;
import com.ibm.zurich.idmx.dm.Nym;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.security.PublicKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.kuleuven.cs.priman.Priman;
import be.kuleuven.cs.priman.credential.Credential;
import be.kuleuven.cs.priman.credential.claim.representation.policy.Policy;
import be.kuleuven.cs.priman.credential.proof.Nonce;
import be.kuleuven.cs.priman.credential.proof.Proof;
import be.kuleuven.cs.priman.manager.CredentialManager;
import be.kuleuven.cs.priman.manager.PersistenceManager;
import be.kuleuven.cs.priman.manager.ServerPolicyManager;
import be.tiemencelis.beans.AuthToken;
import be.tiemencelis.cloudapp.RolesActivity;
import be.tiemencelis.security.SecurityHandler;


/**
 * Created by Tiemen on 18-5-2015.
 * Provides interface for requesting all kinds of contextinformation and saved tokens
 * Also stores cached contextinformation for improved speed and battery consumption
 */

public class ContextManager extends BroadcastReceiver {
    private static final int FIVE_MINUTES = 1000 * 60 * 5;

    private static Context context;
    private static Location lastLocation = null;

    private static WifiManager wifiManager;
    private static WifiInfo currentWifiConnection = null;

    private static BluetoothManager bluetoothManager;
    private static Set<BluetoothDevice> connectedBluetoothDevices;

    private static Map<Long, String> lastNFCTags;

    private static long lastNtpTime = 0;

    private static Map<Map.Entry<String, String>, AuthToken> tokens;

    private static String SERVICE_NAME = "CloudApp";
    private static String SERVICE_TYPE = "_cloudapp._tcp.";
    private static int PORT = 0;
    private static NsdManager mNsdManager;
    private static CredentialIssuer credIssuerServer;
    private static Map<String, Integer> lanClients;


    /**
     * Initialise all context types and listeners
     * @param context context
     */
    public static void init(Context context) {
        ContextManager.context = context;

        /*Request last known and best location as init value*/
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (isBetterLocation(gpsLocation, networkLocation)) {
            lastLocation = gpsLocation;
        }
        else  {
            lastLocation = networkLocation;
        }

        /*Start listener for location updates*/
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 30, 0, locationListener);
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
            System.out.println("Device: " + dev.getName() + ", " + dev.getAddress());
        }

        lastNFCTags = new HashMap<>();
        tokens = new HashMap<>();

        lanClients = new HashMap<>();
        credIssuerServer = new CredentialIssuer();

        //System.out.println("Port: " + PORT);
        //registerService(PORT);

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
        CellLocation loc = telephonyManager.getCellLocation();
        int cid = 0;
        if (loc instanceof GsmCellLocation) {
            cid = ((GsmCellLocation) telephonyManager.getCellLocation()).getCid();
            System.out.println("GSM cid: " + cid);
        } else if (loc instanceof CdmaCellLocation) {
            cid = ((CdmaCellLocation) telephonyManager.getCellLocation()).getBaseStationId();
            System.out.println("CDMA cid: " + cid);
        }

        return cid;
    }


    /**
     * Get the current timestamp based on the machine time
     * @return timestamp in milliseconds (Unix format)
     */
    public static long getSystemTime() {
        return System.currentTimeMillis();
    }


    /**
     * Get a ntp timestamp which is not older than the specified age (based on system time)
     * @param maxAge maximum age in milliseconds!
     * @return timestamp in milliseconds (Unix format)
     * @throws Exception when ntp request failed
     */
    public static long getNtpTime(long maxAge) throws Exception {
        if (maxAge == 0 || (lastNtpTime > System.currentTimeMillis() - maxAge)) {
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
    public static WifiInfo getCurrentWifiConnection() {
        return currentWifiConnection;
    }


    /**
     * Get a list of all configured wifi networks of the device
     * @return List of WifiConfiguration objects
     */
    public static List<WifiConfiguration> getConfiguredWifi() {
        return wifiManager.getConfiguredNetworks();
    }


    /**
     * Get a set of all the connected bluetooth devices.
     * @return Set of BluetoothDevice objects
     */
    public static Set<BluetoothDevice> getConnectedBluetoothDevices() {
        return connectedBluetoothDevices;
    }


    /**
     * Add detected nfc tag and time to gathered tags
     * @param time time of detection
     * @param tag contents as string
     */
    public static void addLastNFCTags(long time, String tag) {
        System.out.println("Time: " + time + " Tag: " + tag);
        ContextManager.lastNFCTags.put(time, tag);
    }


    /**
     * Get all detected NFC tags
     * @return Map with all detected NFC tags
     */
    public static Map<Long, String> getLastNFCTags() {
        return lastNFCTags;
    }


    /**
     * Get all detected NFC tags which are not older than the specified age
     * @param age max age in milliseconds
     * @return detected nfc tags with discovertime which are not too old
     */
    public static Map<Long, String> getValidLastNFCTags(long age) {
        if (age == 0) {
            return lastNFCTags;
        }

        Map<Long, String> result = new HashMap<>();
        long minimumTime = System.currentTimeMillis() - age;

        for (Map.Entry<Long, String> tag : lastNFCTags.entrySet()) {
            if (tag.getKey() > minimumTime) {
                result.put(tag.getKey(), tag.getValue());
            }
        }

        return result;
    }


    /**
     * Add token
     * @param absPath role + rel path of file
     * @param action read or write rights
     * @param token token
     */
    public static void addToken(String absPath, String action, AuthToken token) {
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>(absPath, action);
        /*If action is write, check if there exists an identical token for read rights and remove it*/
        if (action.equals("w")) {
            Map.Entry<String, String> entry2 = new AbstractMap.SimpleEntry<>(absPath, "r");
            if (tokens.get(entry2) != null) {
                tokens.remove(entry2);
            }
        }
        tokens.put(entry, token);
    }


    public static AuthToken getToken(String role, String relPath, String action) {
        Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>(role + relPath, action);

        AuthToken token = tokens.get(entry);
        /*A matching token is found*/
        if (token != null) {
            /*Token is expired, remove it and set null as return value*/
            if (token.getValidUntil() < getSystemTime()) {
                System.out.println("Removing expired token");
                tokens.remove(entry);
                token = null;
            }
        }
        /*No token found, search for token with write action if requested action is read*/
        else if (action.equals("r")) {
            entry.setValue("w");
            token = tokens.get(entry);
            if (token != null) {
            /*Token is expired, remove it and set null as return value*/
                if (token.getValidUntil() < getSystemTime()) {
                    System.out.println("Removing expired token");
                    tokens.remove(entry);
                    token = null;
                }
            }
        }

        return token;
    }


    /**
     * Listener for changes of the wifi connection. SHOULD NOT BE CALLED!
     * @param context current context
     * @param intent intent containing the action
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
                //System.out.println("New location (better): " + location.toString());
            }
            else {
                //System.out.println("New location (worse): " + location.toString());
            }
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


    private static void setPort(int port) {
        PORT = port;
    }


    public static String getCredentialProof(String role, Nonce nonce) throws Exception {
        Map.Entry<String, Integer> client = getFirstLanClient();
        if (client == null) {
            System.out.println("No clients found in network to send to");
            return null;
        }

        URI home = (new File("/sdcard/CloudApp/")).toURI();
        Priman priman = Priman.getInstance();
        PersistenceManager pMgr = priman.getPersistenceManager();
        CredentialManager cMan = priman.getCredentialManager();
        priman.loadConfiguration(home.resolve("app_data/priman.conf"));

        Socket sendSocket = new Socket(client.getKey(), client.getValue());
        ObjectOutputStream dOut = new ObjectOutputStream(sendSocket.getOutputStream());

        /*Send role and nonce*/
        dOut.writeUTF(role);
        dOut.flush();
        dOut.writeObject(nonce);
        dOut.flush();

        ObjectInputStream dIn = new ObjectInputStream(sendSocket.getInputStream());
        /*If client does not own the requested role, abort*/
        String answer = dIn.readUTF();
        if (answer.equals("NO")) {
            System.out.println("Client does not own the requested role");
            dIn.close();
            dOut.close();
            return null;
        }
        else if (!answer.equals("OK")) {
            System.out.println("Wrong message received: " + answer);
            dIn.close();
            dOut.close();
            return null;
        }

        /*Send policy*/
        Policy pol = pMgr.load(home.resolve("app_data/authPolicy.xml"));
        dOut.writeUTF(pol.getStringRepresentation().replace("$ROLE_NAME$", role));
        dOut.flush();

        /*Receive proof*/
        String enc_proof = dIn.readUTF();

        System.out.println("Proof received");

        dIn.close();
        dOut.close();
        sendSocket.close();

        return enc_proof;
    }


    private static class CredentialIssuer {
        ServerSocket serverSocket = null;
        Thread mThread = null;
        PersistenceManager pman = Priman.getInstance().getPersistenceManager();
        ServerPolicyManager spman = Priman.getInstance().getServerPolicyManager();
        CredentialManager credman = Priman.getInstance().getCredentialManager();
        URI home = (new File("/sdcard/CloudApp/")).toURI();
        PublicKey pub_key;

        public CredentialIssuer() {
            try {
                pub_key = SecurityHandler.LoadPublicKey(home.resolve("app_data/auth_public.txt").getPath());
                SecurityHandler.init();
                serverSocket = new ServerSocket(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            setPort(serverSocket.getLocalPort());
            registerService(PORT);
            mThread = new Thread(new IssuerThread());
            mThread.start();
        }

        class IssuerThread implements Runnable {
            @Override
            public void run() {
                try {
                    while(true) {
                        System.out.println("Server issuer listening on port " + serverSocket.getLocalPort());
                        Socket receiveSocket = serverSocket.accept();

                        ObjectInputStream dIn = new ObjectInputStream(receiveSocket.getInputStream());

                        /*Receive role and nonce*/
                        String role = dIn.readUTF();
                        Nonce nonce = (Nonce) dIn.readObject();
                        System.out.println("Client " + receiveSocket.getInetAddress().getHostAddress() + " requesting proof for role " + role);

                        ObjectOutputStream dOut = new ObjectOutputStream(receiveSocket.getOutputStream());
                        /*User does not own the requested role, deny requests*/
                        if (!RolesActivity.getRoles().contains(role)) {
                            dOut.writeUTF("NO");
                            dOut.flush();
                            dOut.close();
                            dIn.close();
                            continue;
                        }

                        /*User does own the requested role, respond with OK*/
                        dOut.writeUTF("OK");
                        dOut.flush();

                        /*Load credential*/
                        List<Credential> creds = new ArrayList<>();
                        Credential cred = pman.load(home.resolve("credentials/cred_user_" + role + ".xml"));
                        MasterSecret ms = new MasterSecret(((MasterSecret) pman.load(home.resolve("credentials/secret_" + role + ".xml"))).getValue(),
                                URI.create("http://cloudservers:8080/gp.xml"), new HashMap<String, Nym>(), new HashMap<String, DomNym>());
                        cred.setSecret(ms);
                        creds.add(cred);

                        System.out.println(creds.get(0).toString());

                        /*Receive role policy*/
                        Policy pol = spman.parsePolicy(dIn.readUTF());
                        System.out.println(pol.toString());

                        /*Create and send proof*/
                        pol.initialize(creds);
                        if (pol.getCredentialClaims().isEmpty()) {
                            System.out.println("Can not satisfy claim");
                            continue;
                        }
                        Proof proof = credman.generateProof(pol.getClaim(), nonce);
                        byte[] enc_proof = SecurityHandler.encrypt(SecurityHandler.serialize(credman.serializeProof(proof)), pub_key);

                        dOut.writeUTF(SecurityHandler.encodeBase64(enc_proof));
                        dOut.flush();
                        System.out.println("Proof of role send");

                        dIn.close();
                        dOut.close();
                        receiveSocket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Client and server code for discovering other CloudApp devices on the network below
     *
     */

    public static void registerService(int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);

        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }


    private static NsdManager.RegistrationListener mRegistrationListener = new NsdManager.RegistrationListener() {

        @Override
        public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
            String mServiceName = NsdServiceInfo.getServiceName();
            SERVICE_NAME = mServiceName;
            System.out.println("Registered name : " + mServiceName + " on port " + NsdServiceInfo.getPort());
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo,
                                         int errorCode) {
            // Registration failed! Put debugging code here to determine
            // why.
            System.out.println("Service register failed " + serviceInfo.toString() + " error: " + errorCode);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            // Service has been unregistered. This only happens when you
            // call
            // NsdManager.unregisterService() and pass in this listener.
            System.out.println("Service Unregistered : " + serviceInfo.getServiceName());
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo,
                                           int errorCode) {
            // Unregistration failed. Put debugging code here to determine
            // why.
            System.out.println("Service unregister failed");
        }
    };




    // Instantiate a new DiscoveryListener
    private static NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

        //  Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
            System.out.println("Service discovery started");
        }

        @Override
        public void onServiceFound(NsdServiceInfo service) {
            // A service was found!  Do something with it.
            //System.out.println("Service discovery success" + service);
            if (!service.getServiceType().equals(SERVICE_TYPE)) {
                // Service type is the string containing the protocol and
                // transport layer for this service.
                System.out.println("Unknown Service Type: " + service.getServiceType());
            } else if (service.getServiceName().equals(SERVICE_NAME)) {
                // The name of the service tells the user what they'd be
                // connecting to. It could be "Bob's Chat App".
                //System.out.println("Same machine: " + SERVICE_NAME);
            } else if (service.getServiceName().contains("CloudApp")){
                mNsdManager.resolveService(service, new MyResolveListener());
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            //System.out.println("service lost" + service);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            System.out.println("Discovery stopped: " + serviceType);
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            System.out.println("Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            System.out.println("Discovery failed: Error code:" + errorCode);
            mNsdManager.stopServiceDiscovery(this);
        }
    };



    //private static NsdManager.ResolveListener mResolveListener =;
    private static class MyResolveListener implements NsdManager.ResolveListener {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails. Use the error code to debug.
            System.out.println("Resolve failed " + errorCode);
            System.out.println("serivce = " + serviceInfo);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            if (!serviceInfo.getServiceName().equals(SERVICE_NAME)) {
                System.out.println("Found at " + serviceInfo.getHost().getHostAddress() + ":" + serviceInfo.getPort());
                addLanClient(serviceInfo.getHost().getHostAddress(), serviceInfo.getPort());
            }
        }
    }


    synchronized private static void addLanClient(String host, int port) {
        if (!lanClients.isEmpty()) {
            lanClients.clear();
        }
        lanClients.put(host, port);
    }


    synchronized public static Map.Entry<String, Integer> getFirstLanClient() {
        if (lanClients.isEmpty()) {
            System.out.println("No lan clients in list to get");
            return null;
        }
        return lanClients.entrySet().iterator().next();
    }


    public static void tearDown() {
        mNsdManager.unregisterService(mRegistrationListener);
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }


}