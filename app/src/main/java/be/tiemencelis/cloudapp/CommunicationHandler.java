package be.tiemencelis.cloudapp;

import android.bluetooth.BluetoothDevice;
import android.location.Location;
import android.net.wifi.WifiInfo;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.kuleuven.cs.priman.Priman;
import be.kuleuven.cs.priman.connection.Connection;
import be.kuleuven.cs.priman.credential.Credential;
import be.kuleuven.cs.priman.credential.issuance.IssuanceSpecification;
import be.kuleuven.cs.priman.credential.proof.Nonce;
import be.kuleuven.cs.priman.credential.proof.Proof;
import be.kuleuven.cs.priman.manager.ConnectionManager;
import be.kuleuven.cs.priman.manager.CredentialManager;
import be.kuleuven.cs.priman.manager.PersistenceManager;
import be.kuleuven.cs.priman.manager.ServerPolicyManager;
import be.kuleuven.cs.primanprovider.connection.ssl.SSLParameters;
import be.tiemencelis.accesspolicy.Policy;
import be.tiemencelis.accesspolicy.PolicyResponse;
import be.tiemencelis.accesspolicy.PolicySet;
import be.tiemencelis.accesspolicy.PolicySetResponse;
import be.tiemencelis.accesspolicy.RequirementItem;
import be.tiemencelis.accesspolicy.RequirementItemResponse;
import be.tiemencelis.beans.AuthToken;
import be.tiemencelis.beans.ConnectInfo;
import be.tiemencelis.beans.FileMeta;
import be.tiemencelis.beans.PolicyResponseRequest;
import be.tiemencelis.context.ContextManager;

/**
 * Created by Tiemen on 12-5-2015.
 *
 */
public class CommunicationHandler {
    private static final Priman priman = Priman.getInstance();
    private static PersistenceManager pman = priman.getPersistenceManager();
    private static ServerPolicyManager spman = priman.getServerPolicyManager();
    private static ConnectionManager cman = priman.getConnectionManager();
    private static CredentialManager credman = priman.getCredentialManager();
    private static final URI home = (new File("/sdcard/CloudApp/")).toURI();
    private static final SSLParameters cloudParam = pman.load(home.resolve("app_data/cloudConnection-ssl.param"));
    private static final SSLParameters verificationParam = pman.load(home.resolve("app_data/verificationConnection-ssl.param"));
    private static final SSLParameters caParam = pman.load(home.resolve("app_data/caConnection-ssl.param"));


    public static boolean requestCredential(String role) throws Exception {
        Connection conn = cman.getConnection(cloudParam);

        /*Start request create account*/
        conn.send("CREATE_ACCOUNT");
        conn.send(role);
        /*Rol already exists, cancel*/
        if (!((String) conn.receive()).equals("OK")) {
            conn.close();
            return false;
        }

        /*Request credential for provided role name at the CA*/
        try {
            Connection conn2 = cman.getConnection(caParam);
            IssuanceSpecification ispec = pman.load(home.resolve("app_data/idmxIssuanceSpecification.xml"));
            Map<String, String> values = new HashMap<>();
            values.put("Name", role);
            ispec.setValues(values);
            //SEND IT TO THE ISSUER
            conn2.send(ispec.getRequestForIssuer());
            //INITIATE THE ISSUING PROTOCOL
            Credential cred = credman.getIssuedCredential(conn2, ispec);
            pman.store(cred, home.resolve("credentials/cred_user_" + role + ".xml"));
            pman.store(cred.getSecret(), home.resolve("credentials/secret_" + role + ".xml"));
            conn2.close();
        }
        /*Requesting credential failed, cancel protocol*/
        catch (Exception e) {
            conn.send("NOK");
            conn.close();
            return false;
        }

        /*Credential successfully obtained*/
        conn.send("OK");
        /*Creation of account failed, cancel protocol and remove credential*/
        if (!((String) conn.receive()).equals("OK")) {
            File credential = new File(home.resolve("credentials/cred_user_" + role + ".xml"));
            credential.delete();
            credential = new File(home.resolve("credentials/secret_" + role + ".xml"));
            credential.delete();
            conn.close();
            return false;
        }
        /*Account successfully created*/
        return true;
    }


    @SuppressWarnings("unchecked")
    public static ArrayList<FileMeta> requestDirectoryContents(String role, String location) throws Exception {
        ArrayList<FileMeta> result = null;

        Connection conn = cman.getConnection(cloudParam);

        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, "r");
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        } else {
            System.out.println("No token found, requesting without one");
        }

        conn.send("REQUEST_FILE");
        conn.send(info);

        switch ((String) conn.receive()) {
            case "OK":
                System.out.println("Token valid: receiving contents");
                result = (ArrayList<FileMeta>) conn.receive();
                conn.close();
                break;
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                AuthToken token = getToken(role, "r", session);
                if (token != null) {
                    ContextManager.addToken(role + location, "r", token);
                    System.out.println("Token received and saved");
                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    conn.send("REQUEST_FILE");
                    conn.send(info);

                    if (((String) conn.receive()).equals("OK")) {
                        System.out.println("Token valid: receiving contents");
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

        Connection conn = cman.getConnection(cloudParam);

        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, "r");
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        } else {
            System.out.println("No token found, requesting without one");
        }

        conn.send("REQUEST_FILE");
        conn.send(info);

        switch ((String) conn.receive()) {
            case "OK":
                System.out.println("Token valid: receiving contents");
                result = (byte[]) conn.receive();
                conn.close();
                break;
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                AuthToken token = getToken(role, "r", session);
                if (token != null) {
                    ContextManager.addToken(role + location, "r", token);
                    System.out.println("Token received and saved");
                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    conn.send("REQUEST_FILE");
                    conn.send(info);

                    if (((String) conn.receive()).equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        result = (byte[]) conn.receive();
                        conn.close();
                    }
                }

                break;
        }


        return result;
    }


    @SuppressWarnings("unchecked")
    private static AuthToken getToken(String role, String action, UUID session) throws Exception {
        AuthToken token = null;

        Connection conn = cman.getConnection(verificationParam);

        conn.send("AUTHENTICATE");
        conn.send(role); //TODO proof ook zenden
        conn.send(action);
        conn.send(session);

        /*Load credential*/
        List<Credential> creds = new ArrayList<>();
        Credential cred = pman.load(home.resolve("credentials/cred_user_" + role + ".xml"));
        cred.setSecret(pman.load(home.resolve("credentials/secret_" + role + ".xml")));
        creds.add(cred);

        /*Receive role policy and nonce*/
        be.kuleuven.cs.priman.credential.claim.representation.policy.Policy pol = spman.parsePolicy((String) conn.receive());
        Nonce nonce = (Nonce) conn.receive();

        /*Receive PolicyResponseRequest*/
        PolicyResponseRequest request = (PolicyResponseRequest) conn.receive();

        /*Create role proof and PolicySetResponse*/
        pol.initialize(creds);
        if (pol.getCredentialClaims().isEmpty()) {
            System.out.println("Can not satisfy claim");
            return null;
        }
        Proof proof = credman.generateProof(pol.getClaim(), nonce);
        System.out.println("Proof is valid: " + proof.isValid());
        System.out.println("Proof satistfies policy: " + proof.satisfiesPolicy(pol));

        PolicySetResponse resp = createAnswer(request);

        /*Send PolicySetResponse and role proof*/
        conn.send(resp);
        conn.send(credman.serializeProof(proof));

        if (((String) conn.receive()).equals("OK")) {
            token = (AuthToken) conn.receive();
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
                        response.addValue(Integer.toString(cid) + ":" + System.currentTimeMillis());
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
                        response.addValue(device.getAddress() + ":" + time);
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
