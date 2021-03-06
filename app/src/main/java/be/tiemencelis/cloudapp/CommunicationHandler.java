package be.tiemencelis.cloudapp;

import android.bluetooth.BluetoothDevice;
import android.location.Location;
import android.net.wifi.WifiInfo;

import com.ibm.zurich.idmx.dm.DomNym;
import com.ibm.zurich.idmx.dm.MasterSecret;
import com.ibm.zurich.idmx.dm.Nym;

import java.io.File;
import java.io.FilenameFilter;
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
import be.tiemencelis.security.SecurityHandler;

/**
 * Created by Tiemen on 12-5-2015.
 * Used for handling all communication of the app: requesting data, policies, authentication, token request, ...
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


    /**
     * Create a new account on the cloud system
     * @param role name of the new account
     * @param admin admin rights or not
     * @return true if successful
     * @throws Exception
     */
    public static boolean createAccount(String role, int admin) throws Exception {
        Connection conn = cman.getConnection(cloudParam);

        /*Start request create account*/
        conn.send("CREATE_ACCOUNT");
        conn.send(role);
        conn.send(admin);
        /*Rol already exists, cancel*/
        if (!conn.receive().equals("OK")) {
            conn.close();
            return false;
        }

        /*Request credential for provided role name at the CA*/
        try {
            requestCredential(role);
        }
        /*Requesting credential failed, cancel protocol*/
        catch (Exception e) {
            conn.send("NOK");
            conn.close();
            e.printStackTrace();
            return false;
        }

        /*Credential successfully obtained*/
        conn.send("CREDENTIAL_OBTAINED");
        /*Creation of account failed, cancel protocol and remove credential*/
        if (!conn.receive().equals("ACCOUNT_CREATED")) {
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


    /**
     * Request a credential for a role or username from the CA
     * @param role role/username for the credential
     * @return true if successful
     * @throws Exception
     */
    public static boolean requestCredential(String role) throws Exception {
        Connection conn = cman.getConnection(caParam);
        IssuanceSpecification ispec = pman.load(home.resolve("app_data/idmxIssuanceSpecification.xml"));
        Map<String, String> values = new HashMap<>();
        values.put("Name", role);
        ispec.setValues(values);
        //SEND IT TO THE ISSUER
        conn.send(ispec.getRequestForIssuer());
        //INITIATE THE ISSUING PROTOCOL
        Credential cred = credman.getIssuedCredential(conn, ispec);
        pman.store(cred, home.resolve("credentials/cred_user_" + role + ".xml"));
        pman.store(cred.getSecret(), home.resolve("credentials/secret_" + role + ".xml"));
        conn.close();

        return true;
    }


    /*TODO new policy ipv copy*/
    /**
     * Share data with another user or role. Currently only with the same policies
     * @param role current role to be used for the sharing
     * @param shareRole destination role, account that will receive access to the data
     * @param location full path the user knows of the data to share
     * @throws Exception
     */
    public static void shareData(String role, String shareRole, String location) throws Exception {
        Connection conn = cman.getConnection(cloudParam);

        /*Gather connect info and possible saved token*/
        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, "r");
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        }

        /*Start share data process*/
        conn.send("SHARE_DATA");
        conn.send(info);

        /*Proof of role*/
        SendRoleProof(conn, role);

        switch ((String) conn.receive()) {
            /*Token is accepted and data will be shared*/
            case "OK":
                System.out.println("Token valid: send role name to share with");
                conn.send("COPY");
                conn.send(shareRole);
                if (!conn.receive().equals("OK")) {
                    conn.close();
                    throw new Exception("Error sharing data with " + shareRole);
                }
                conn.close();
                break;
            /*Invalid token, request one first and retry*/
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                /*Request token from verification server*/
                AuthToken token = getToken(role, "r", session);
                if (token != null) {
                    /*Save token*/
                    ContextManager.addToken(role + location, "r", token);
                    System.out.println("Token received and saved");
                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    /*Retry share process*/
                    conn.send("SHARE_DATA");
                    conn.send(info);
                    SendRoleProof(conn, role);

                    /*Token and proof correct, data will be shared*/
                    if (conn.receive().equals("OK")) {
                        System.out.println("Token valid: send role name to share with");
                        conn.send("COPY");
                        conn.send(shareRole);
                        if (!conn.receive().equals("OK")) {
                            conn.close();
                            throw new Exception("(Server) Error sharing data with " + shareRole);
                        }
                    } else {
                        conn.close();
                        throw new Exception("(Token) Error sharing data with " + shareRole);
                    }
                    conn.close();
                }
                break;
            /*User has no admin rights*/
            default:
                System.out.println("No admin rights, data sharing denied");
                conn.close();
                throw new Exception("No admin rights, data sharing denied");
        }
    }


    /**
     * Request the contents of a directory
     * @param role role currently using
     * @param location relative path of the directory
     * @return List of FileMeta objects which represents metadata info of every file/dir
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<FileMeta> requestDirectoryContents(String role, String location) throws Exception {
        ArrayList<FileMeta> result = null;
        Connection conn = cman.getConnection(cloudParam);

        /*Gather connect info and possible saved token*/
        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, "r");
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        }

        /*Start request data process*/
        conn.send("REQUEST_FILE");
        conn.send(info);

        /*Authenticate role*/
        SendRoleProof(conn, role);

        switch ((String) conn.receive()) {
            /*Token correct, receive content*/
            case "OK":
                System.out.println("Token valid: receiving contents");
                result = (ArrayList<FileMeta>) conn.receive();
                conn.close();
                break;
            /*Authorization is needed, request a token first*/
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                /*Request a token from the verification server*/
                AuthToken token = getToken(role, "r", session);
                if (token != null) {
                    ContextManager.addToken(role + location, "r", token);
                    System.out.println("Token received and saved");

                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    /*Retry request process*/
                    conn.send("REQUEST_FILE");
                    conn.send(info);

                    SendRoleProof(conn, role);

                    if (conn.receive().equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        result = (ArrayList<FileMeta>) conn.receive();
                        conn.close();
                    }
                }

                break;
        }

        return result;
    }


    /**
     * Request a file
     * @param role role to be used for the request
     * @param location relative path of the file
     * @return full contents of the file as a byte array
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
     public static byte[] requestFileContents(String role, String location) throws Exception {
        byte[] result = null;

        Connection conn = cman.getConnection(cloudParam);

        /*Gather connect info and possible saved token*/
        ConnectInfo info = new ConnectInfo();
        info.setAction("r");
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, "r");
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        }

        /*Start request process*/
        conn.send("REQUEST_FILE");
        conn.send(info);

        /*Authenticate role*/
        SendRoleProof(conn, role);

        switch ((String) conn.receive()) {
            /*Token correct, receive file*/
            case "OK":
                System.out.println("Token valid: receiving contents");
                result = (byte[]) conn.receive();
                conn.close();
                break;
            /*Authorization is needed, request a token first*/
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                /*Request a token from the verification server*/
                AuthToken token = getToken(role, "r", session);
                if (token != null) {
                    ContextManager.addToken(role + location, "r", token);
                    System.out.println("Token received and saved");
                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    /*Retry request process*/
                    conn.send("REQUEST_FILE");
                    conn.send(info);

                    SendRoleProof(conn, role);

                    if (conn.receive().equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        result = (byte[]) conn.receive();
                        conn.close();
                    }
                }

                break;
        }

        return result;
    }


    /**
     * Request the access policy for a certain file or directory
     * @param role role to be used
     * @param location relative location of the file/dir of which the policy is requested
     * @param action whether the read or write policy is requested
     * @return Full PolicySet, external credentials currently not supported and ignored (but still received)
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static PolicySet requestPolicySet(String role, String location, String action) throws Exception {
        PolicySet result = null;

        Connection conn = cman.getConnection(cloudParam);

        /*Gather connect info and possible saved token*/
        ConnectInfo info = new ConnectInfo();
        info.setAction(action);
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, action);
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        }

        /*Start request policy process*/
        conn.send("REQUEST_POLICY");
        conn.send(info);

        /*Authenticate role*/
        SendRoleProof(conn, role);

        switch ((String) conn.receive()) {
            /*Token correct, receive PolicySet*/
            case "OK":
                System.out.println("Token valid: receiving PolicySet");
                result = (PolicySet) conn.receive();
                conn.receive(); //Receive external credential policies here, currently not used
                conn.close();
                break;
            /*Authorization is needed, request a token first*/
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                /*Request a token from the verification server*/
                AuthToken token = getToken(role, action, session);
                if (token != null) {
                    ContextManager.addToken(role + location, action, token);
                    System.out.println("Token received and saved");
                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    /*Retry request policy process*/
                    conn.send("REQUEST_POLICY");
                    conn.send(info);

                    SendRoleProof(conn, role);

                    if (conn.receive().equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        result = (PolicySet) conn.receive();
                        conn.receive(); //TODO Receive external credential policies here, currently not used
                        conn.close();
                    }
                }
                break;
        }

        return result;
    }


    /**
     * Update an existing access policy with a new PolicySet
     * @param role role to be used
     * @param location relative location of the file/dir matching the access policy
     * @param policySet new access policy
     * @return true if successfull
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static boolean savePolicySet(String role, String location, PolicySet policySet) throws Exception {
        PolicySet result = null;
        String action = "w";

        Connection conn = cman.getConnection(cloudParam);

        /*Gather connect info and possible saved token*/
        ConnectInfo info = new ConnectInfo();
        info.setAction(action);
        info.setRel_location(location);
        info.setRole(role);
        AuthToken saved = ContextManager.getToken(role, location, action);
        if (saved != null) {
            System.out.println("Reusing saved token");
            info.setAuthToken(saved);
        }

        /*Start push policy process*/
        conn.send("PUSH_POLICY");
        conn.send(info);

        /*Authenticate role*/
        SendRoleProof(conn, role);

        switch ((String) conn.receive()) {
            /*Token correct, send new PolicySet*/
            case "OK":
                System.out.println("Token valid: receiving PolicySet");
                conn.send(policySet);
                conn.close();
                return true;
            /*Authorization is needed, request a token first*/
            case "AUTHENTICATE":
                System.out.println("Need to authenticate");
                UUID session = (UUID) conn.receive();
                conn.close();

                /*Request a token from the verification server*/
                AuthToken token = getToken(role, action, session);
                if (token != null) {
                    ContextManager.addToken(role + location, action, token);
                    System.out.println("Token received and saved");
                    conn = cman.getConnection(cloudParam);
                    info.setAuthToken(token);

                    /*Retry push policy process*/
                    conn.send("PUSH_POLICY");
                    conn.send(info);

                    SendRoleProof(conn, role);

                    if (conn.receive().equals("OK")) {
                        System.out.println("Token valid: receiving contents");
                        conn.send(policySet);
                        conn.close();
                        return true;
                    }
                }
                break;
            case "NOK":
                return false;
        }

        return false;
    }


    /**
     * Authenticate with the storage server by sending a role proof
     * @param conn connection with storage server
     * @param role role to be used for generating a proof
     * @throws Exception
     */
    private static void SendRoleProof(Connection conn, String role) throws Exception {
        /*Load credential*/
        List<Credential> creds = new ArrayList<>();
        Credential cred = pman.load(home.resolve("credentials/cred_user_" + role + ".xml"));
        MasterSecret ms = new MasterSecret(((MasterSecret) pman.load(home.resolve("credentials/secret_" + role + ".xml"))).getValue(),
                URI.create("http://cloudservers:8080/gp.xml"), new HashMap<String, Nym>(), new HashMap<String, DomNym>());
        cred.setSecret(ms);
        creds.add(cred);

        /*Receive role policy and nonce*/
        be.kuleuven.cs.priman.credential.claim.representation.policy.Policy pol = spman.parsePolicy((String)conn.receive());
        Nonce nonce = (Nonce) conn.receive();
        pol.initialize(creds);
        if (pol.getCredentialClaims().isEmpty()) {
            throw new Exception("Cannot satisfy claim");
        }

        /*Generate proof and send it*/
        Proof proof = credman.generateProof(pol.getClaim(), nonce);
        conn.send(credman.serializeProof(proof));
    }


    /**
     * Request an access token from the verification server
     * @param role role to be used for the token
     * @param action the action rights the user requests to be in the token (read or write)
     * @param session session ID to initialize to communication, received earlier from the storage server
     * @return The received access token or null if not received
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private static AuthToken getToken(String role, String action, UUID session) throws Exception {
        AuthToken token = null;

        Connection conn = cman.getConnection(verificationParam);

        /*Start process*/
        conn.send("AUTHENTICATE");
        conn.send(role);
        conn.send(action);
        conn.send(session);

        /*Load credential*/
        List<Credential> creds = new ArrayList<>();
        Credential cred = pman.load(home.resolve("credentials/cred_user_" + role + ".xml"));
        MasterSecret ms = new MasterSecret(((MasterSecret) pman.load(home.resolve("credentials/secret_" + role + ".xml"))).getValue(),
                            URI.create("http://cloudservers:8080/gp.xml"), new HashMap<String, Nym>(), new HashMap<String, DomNym>());
        cred.setSecret(ms);
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
        /*System.out.println("Proof is valid: " + proof.isValid());
        System.out.println("Proof satistfies policy: " + proof.satisfiesPolicy(pol));*/

        /*Create an answer for the received access policy*/
        PolicySetResponse resp = createAnswer(request);

        /*Send PolicySetResponse and role proof*/
        conn.send(resp);
        conn.send(credman.serializeProof(proof));

        /*Answer is correct, receive access token*/
        if (conn.receive().equals("OK")) {
            token = (AuthToken) conn.receive();
        }
        conn.close();

        return token;
    }


    /**
     * Create an answer for an access policy
     * @param request Full access policy, including external credential policies and nonces
     * @return PolicySetResponse with all the answers, answers for external credential policies are also included in Base64 encoding
     * @throws Exception
     */
    private static PolicySetResponse createAnswer(PolicyResponseRequest request) throws Exception {
        PolicySetResponse result = new PolicySetResponse();

        PolicySet policySet = request.getAccessPolicy();
        result.setId(policySet.getId());

        /*try to create an answer for every policy from the policy set*/
        for (Policy policy : policySet.getPolicies()) {
            PolicyResponse polRes = new PolicyResponse();
            long age = policy.getAgeInMilliseconds();

            /*try to create an answer for every item of a policy*/
            for (RequirementItem item : policy.getRequirementItems()) {
                RequirementItemResponse itemRes = null;

                /*handle every item type*/
                switch (item.getType()) {
                    case "context":
                        itemRes = handleContext(age, item, request.getCredentialNonces().get(item.getId()));
                        break;
                    case "x509":
                        itemRes = handleX509(request.getCredentialPolicies().get(item.getId()), request.getCredentialNonces().get(item.getId()));
                        break;
                    case "idmx":
                        itemRes = handleIdemix(request.getCredentialPolicies().get(item.getId()), request.getCredentialNonces().get(item.getId()));
                        break;
                    default:
                        System.out.println("Unsupported type \"" + item.getType() + "\" in requirementItem");
                        continue;
                }

                /*an answer for the current item is found, add to the response*/
                if (itemRes != null) {
                    itemRes.setId(item.getId());
                    polRes.addItem(itemRes);
                }
            }

            /*an answer for the current policy is found, add to the response*/
            if (!polRes.getItems().isEmpty()) {
                polRes.setId(policy.getId());
                result.addPolicy(polRes);
            }
        }
        return result;
    }


    /**
     * Create an answer for a context rule, every context type is handled here
     * @param age max age of the answer
     * @param item context item
     * @param nonce nonce if necessary (only with user-role-nearby-wlan) otherwise null
     * @return RequirementItemResponse containing the answer, or null
     */
    private static RequirementItemResponse handleContext(long age, RequirementItem item, Nonce nonce) {
        RequirementItemResponse response = new RequirementItemResponse();

        try {
            /*Handle context types separate, answer source depends on trust level in certain cases*/
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
                    if (response.getValues().isEmpty()) {
                        return null;
                    }
                    break;
                case "user-role-nearby-wlan":
                    if (nonce == null) {
                        return null;
                    }
                    String proof = ContextManager.getCredentialProof(item.getOperation().getValue(0), nonce);
                    if (proof != null) {
                        response.addValue(proof);
                    }
                    if (response.getValues().isEmpty()) {
                        return null;
                    }
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


    /**
     * Create a response for an x509 item if possible
     * @param credential x509 policy
     * @param nonce nonce to be used in the policy
     * @return RequirementItemResponse containing the proof, null if not possible or error. Encoded in Base64 after serialization
     */
    private static RequirementItemResponse handleX509(String credential , Nonce nonce) {
        RequirementItemResponse response;

        /*Load credentials*/
        File credentialFolder = new File("/sdcard/CloudApp/credentials/");
        FilenameFilter credsOnlyFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("cred_user_");
            }
        };
        File[] credentials = credentialFolder.listFiles(credsOnlyFilter);
        List<Credential> creds = new ArrayList<>();
        for (File file : credentials) {
            Credential cred = pman.load(file.toURI());
            MasterSecret ms = new MasterSecret(((MasterSecret) pman.load(home.resolve("credentials/secret_" + file.getName().substring(10, file.getName().length()-4) + ".pem"))).getValue(),
                    URI.create("http://cloudservers:8080/gp.xml"), new HashMap<String, Nym>(), new HashMap<String, DomNym>());
            cred.setSecret(ms);
            creds.add(cred);
        }

        /*Initialize x509 credential*/
        be.kuleuven.cs.priman.credential.claim.representation.policy.Policy pol = spman.parsePolicy(credential);
        pol.initialize(creds);
        if (pol.getCredentialClaims().isEmpty()) {
            System.out.println("No credential can be used to create a proof for the policy");
            return null;
        }

        /*Create proof and add to response if possible*/
        try {
            Proof proof = credman.generateProof(pol.getClaim(), nonce);
            response = new RequirementItemResponse();
            response.addValue(SecurityHandler.encodeBase64(SecurityHandler.serialize(credman.serializeProof(proof))));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception when creating x509 proof for credential policy");
            return null;
        }

        return response;
    }


    /**
     * Create a response for an idemix item if possible
     * @param credential idemix policy
     * @param nonce nonce to be used in the proof
     * @return RequirementItemResponse containing the proof, null if not possible or error. Encoded in Base64 after serialization
     */
    private static RequirementItemResponse handleIdemix(String credential , Nonce nonce) {
        RequirementItemResponse response;

        /*Load credentials*/
        File credentialFolder = new File("/sdcard/CloudApp/credentials/");
        FilenameFilter credsOnlyFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("cred_user_");
            }
        };
        File[] credentials = credentialFolder.listFiles(credsOnlyFilter);
        List<Credential> creds = new ArrayList<>();
        for (File file : credentials) {
            Credential cred = pman.load(file.toURI());
            MasterSecret ms = new MasterSecret(((MasterSecret) pman.load(home.resolve("credentials/secret_" + file.getName().substring(10, file.getName().length()-4) + ".xml"))).getValue(),
                    URI.create("http://cloudservers:8080/gp.xml"), new HashMap<String, Nym>(), new HashMap<String, DomNym>());
            cred.setSecret(ms);
            creds.add(cred);
        }

        /*Initialize idemix credential*/
        be.kuleuven.cs.priman.credential.claim.representation.policy.Policy pol = spman.parsePolicy(credential);
        pol.initialize(creds);
        if (pol.getCredentialClaims().isEmpty()) {
            System.out.println("No credential can be used to create a proof for the policy");
            return null;
        }

        /*Create proof and add to response if possible*/
        try {
            Proof proof = credman.generateProof(pol.getClaim(), nonce);
            response = new RequirementItemResponse();
            response.addValue(SecurityHandler.encodeBase64(SecurityHandler.serialize(credman.serializeProof(proof))));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception when creating idemix proof for credential policy");
            return null;
        }

        return response;
    }


}
