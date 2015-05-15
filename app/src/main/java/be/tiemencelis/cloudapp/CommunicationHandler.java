package be.tiemencelis.cloudapp;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import be.kuleuven.cs.priman.Priman;
import be.kuleuven.cs.priman.connection.Connection;
import be.kuleuven.cs.primanprovider.connection.ssl.SSLParameters;
import be.tiemencelis.beans.ConnectInfo;
import be.tiemencelis.beans.FileMeta;

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
        //TODO Create answer
        //TODO conn.send(PolicySetResponse)

        if (((String) conn.receive()).equals("OK")) {
            token = (Map<String, byte[]>) conn.receive();
            long until = (long) conn.receive();
            //TODO store token + until
        }
        conn.close();

        return token;
    }


}