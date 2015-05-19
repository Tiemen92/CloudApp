package be.tiemencelis.cloudapp;

import android.bluetooth.BluetoothDevice;
import android.location.Location;
import android.net.wifi.WifiInfo;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.kuleuven.cs.priman.Priman;
import be.kuleuven.cs.priman.connection.Connection;
import be.kuleuven.cs.primanprovider.connection.ssl.SSLParameters;
import be.tiemencelis.accesspolicy.AccessPolicyParser;
import be.tiemencelis.accesspolicy.Policy;
import be.tiemencelis.accesspolicy.PolicyResponse;
import be.tiemencelis.accesspolicy.PolicySet;
import be.tiemencelis.accesspolicy.PolicySetResponse;
import be.tiemencelis.accesspolicy.RequirementItem;
import be.tiemencelis.accesspolicy.RequirementItemResponse;
import be.tiemencelis.beans.ConnectInfo;
import be.tiemencelis.beans.FileMeta;
import be.tiemencelis.beans.PolicyResponseRequest;
import be.tiemencelis.context.ContextManager;

/**
 * Created by Tiemen on 12-5-2015.
 *
 */
public class CommunicationHandler {
    private static final URI home = (new File("/sdcard/CloudApp/")).toURI();
    private static final SSLParameters cloudParam = Priman.getInstance().getPersistenceManager().load(home.resolve("cloudConnection-ssl.param"));
    private static final SSLParameters verificationParam = Priman.getInstance().getPersistenceManager().load(home.resolve("verificationConnection-ssl.param"));


    @SuppressWarnings("unchecked")
    public static ArrayList<FileMeta> requestDirectoryContents(String role, String location) throws Exception {
        ArrayList<FileMeta> result = null;
        //FileMeta meta;

        Connection conn = Priman.getInstance().getConnectionManager().getConnection(cloudParam);

        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);

        conn.send("REQUEST_FILE");
        conn.send(info);

        switch ((String) conn.receive()) {
            case "OK":
                System.out.println("Token valid: receiving contents");
                //meta = (FileMeta) conn.receive(); TODO momenteel niet nodig
                result = (ArrayList<FileMeta>) conn.receive();
                conn.close();
                break;
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                Map<String, byte[]> token = getToken(role, "r", session);
                if (token != null) {
                    System.out.println("Token received");
                    conn = Priman.getInstance().getConnectionManager().getConnection(cloudParam);
                    info.setAuthToken(token);

                    conn.send("REQUEST_FILE");
                    conn.send(info);

                    if (((String) conn.receive()).equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        //meta = (FileMeta) conn.receive(); TODO momenteel niet nodig
                        result = (ArrayList<FileMeta>) conn.receive();
                        conn.close();
                    }
                }

                break;
        }


        return result;
    }


    @SuppressWarnings("unchecked")
    public static byte[] requestFileContents(String role, String location) throws Exception {
        byte[] result = null;

        Connection conn = Priman.getInstance().getConnectionManager().getConnection(cloudParam);

        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);

        conn.send("REQUEST_FILE");
        conn.send(info);

        switch ((String) conn.receive()) {
            case "OK":
                System.out.println("Token valid: receiving contents");
                //meta = (FileMeta) conn.receive(); TODO momenteel niet nodig
                result = (byte[]) conn.receive();
                conn.close();
                break;
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                Map<String, byte[]> token = getToken(role, "r", session);
                if (token != null) {
                    System.out.println("Token received");
                    conn = Priman.getInstance().getConnectionManager().getConnection(cloudParam);
                    info.setAuthToken(token);

                    conn.send("REQUEST_FILE");
                    conn.send(info);

                    if (((String) conn.receive()).equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        //meta = (FileMeta) conn.receive(); TODO momenteel niet nodig
                        result = (byte[]) conn.receive();
                        conn.close();
                    }
                }

                break;
        }


        return result;
    }


    @SuppressWarnings("unchecked")
    private static Map<String, byte[]> getToken(String role, String action, UUID session) throws Exception {
        Map<String, byte[]> token = null;

        Connection conn = Priman.getInstance().getConnectionManager().getConnection(verificationParam);

        conn.send("AUTHENTICATE");
        conn.send(role);
        conn.send(action);
        conn.send(session);

        //TODO PolicyResponseRequest request = (PolicyResponseRequest) conn.receive();
        //PolicyResponseRequest request = (PolicyResponseRequest) conn.receive();
        //TODO Create answer + role proof
        //TODO conn.send(PolicySetResponse)

        if (((String) conn.receive()).equals("OK")) {
            token = (Map<String, byte[]>) conn.receive();
            long until = (long) conn.receive();
            //TODO store token + until
        }
        conn.close();

        return token;
    }


    private static PolicySetResponse createAnswer(PolicyResponseRequest request) throws Exception {
        PolicySetResponse result = new PolicySetResponse();

        PolicySet policySet = request.getAccessPolicy();
        result.setId(policySet.getId());

        for (Policy policy : policySet.getPolicies()) {
            PolicyResponse polRes = new PolicyResponse();
            long age = policy.getAgeInMilliseconds();

            for (RequirementItem item : policy.getRequirementItems()) {
                RequirementItemResponse itemRes = null;

                switch (item.getType()) {
                    case "context":
                        itemRes = handleContext(age, item);
                        break;
                    case "x509":
                        itemRes = handleX509(request.getCredentialPolicies().get(item.getId()));
                        break;
                    case "idmx":
                        itemRes = handleIdemix(request.getCredentialPolicies().get(item.getId()));
                        break;
                    default:
                        System.out.println("Unsupported type \"" + item.getType() + "\" in requirementItem");
                        continue;
                }

                if (itemRes != null) {
                    itemRes.setId(item.getId());
                    polRes.addItem(itemRes);
                }
            }

            if (!polRes.getItems().isEmpty()) {
                polRes.setId(policy.getId());
                result.addPolicy(polRes);
            }
        }

        return result;
    }


    private static RequirementItemResponse handleContext(long age, RequirementItem item) {
        RequirementItemResponse response = new RequirementItemResponse();

        try {
            switch (item.getData().getDataType()) {
                case "location":
                    if (item.getOperation().getName().equals("equals-cell-id")) {
                        int cid = ContextManager.getCellId();
                        if (cid == -1) {
                            return null;
                        }
                        response.addValue(Integer.toString(cid));
                    }
                    else {
                        Location loc = ContextManager.getLocation();
                        if (loc == null) {
                            return null;
                        }
                        response.addValue(loc.getLatitude() + "," + loc.getLongitude() + ":" + loc.getTime());
                    }
                    break;
                case "ntp":
                    if (item.getData().getMinTrust() > 1) {
                        response.addValue(Long.toString(ContextManager.getNtpTime(age)));
                    } else {
                        response.addValue(Long.toString(ContextManager.getSystemTime()));
                    }
                    break;
                case "connected-wlan":
                    WifiInfo wifi = ContextManager.getCurrentWifiConnection();
                    if (wifi == null) {
                        return null;
                    }
                    response.addValue(wifi.getSSID() + ":" + ContextManager.getSystemTime());
                    break;
                case "paired-bluetooth":
                    Set<BluetoothDevice> devices = ContextManager.getConnectedBluetoothDevices();
                    if (devices == null || devices.isEmpty()) {
                        return null;
                    }
                    long time = ContextManager.getSystemTime();
                    for (BluetoothDevice device : devices) {
                        response.addValue(device.getName() + ":" + time);
                    }
                    break;
                case "nfc-id":
                    Map<Long, String> tags = ContextManager.getValidLastNFCTags(age);
                    for(Map.Entry<Long, String> tag : tags.entrySet()) {
                        response.addValue(tag.getValue() + ":" + tag.getKey());
                    }
                    break;
                case "user-role-nearby-wlan":
                    //TODO
                    break;
                default:
                    System.out.println("Unsupported context type \"" + item.getData().getDataType() + "\" in RequirementData");
                    return null;
            }
        } catch (Exception e) {
            System.out.println("Exception when gathering context");
            e.printStackTrace();
            return null;
        }

        return response;
    }


    private static RequirementItemResponse handleX509(String credential /*, Nonce nonce*/) { //TODO nonce
        RequirementItemResponse response = null;



        return response;
    }


    private static RequirementItemResponse handleIdemix(String credential /*, Nonce nonce*/) { //TODO nonce
        RequirementItemResponse response = null;



        return response;
    }


}
