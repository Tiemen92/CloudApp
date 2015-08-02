package be.tiemencelis.cloudapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import be.tiemencelis.beans.FileMeta;
import be.tiemencelis.context.ContextManager;
import be.tiemencelis.context.NfcActivity;


public class RolesActivity extends AppCompatActivity {
    private String[] roles;
    private static final URI home = (new File("/sdcard/CloudApp/")).toURI();

    public RolesActivity() {
        LoadRoles();
    }


    private void LoadRoles() {
        File credentialFolder = new File("/sdcard/CloudApp/credentials/");
        FilenameFilter credsOnlyFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.startsWith("cred_user_")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        File[] credentials = credentialFolder.listFiles(credsOnlyFilter);
        List<String> roleNames = new ArrayList<>();
        for (File cred : credentials) {
            roleNames.add(cred.getName().substring(10, cred.getName().length()-4));
            //System.out.println("Credential found: " + cred.getName().substring(10, cred.getName().length()-4));
        }
        Collections.sort(roleNames);
        roles =  roleNames.toArray(new String[roleNames.size()]);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roles);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roles));

        ContextManager.init(this);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                new Thread(new LoadContents(position)).start();
            }

            class LoadContents implements Runnable {
                int position;

                LoadContents(int position) {
                    this.position = position;
                }

                @Override
                public void run() {
                    ArrayList<FileMeta> files = new ArrayList<>();
                    Bundle b;
                    Intent i;
                    try {
                        files = CommunicationHandler.requestDirectoryContents(roles[position], "/");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (files == null) {
                        showToast("Authentication failed");
                    } else {
                        b = new Bundle();
                        b.putString("location", "/");
                        b.putString("role", roles[position]);
                        b.putSerializable("files", files);
                        i = new Intent(RolesActivity.this, FileBrowserActivity.class);
                        i.putExtras(b);
                        startActivity(i);
                    }
                }
            }
        });



        Button createButton = (Button) findViewById(R.id.bCreate);
        createButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                LayoutInflater inflater = getLayoutInflater();
                View layout = inflater.inflate(R.layout.input_dialog, (ViewGroup) findViewById(R.id.layout_root));
                final EditText input = (EditText) layout.findViewById(R.id.editTextDialog);
                input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());

                new AlertDialog.Builder(RolesActivity.this)
                        .setTitle("Create account")
                        .setMessage("Enter your name/role here:")
                        .setView(layout)
                        .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                requestCredential(input.getEditableText().toString());
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                showToast("Create new account canceled");
                                dialog.cancel();
                            }
                        })
                        .create().show();


                //Intent i = new Intent(RolesActivity.this, NfcActivity.class);
                //startActivity(i);

                try {
                    /*
                    //////
                    //Authenticate
                    //////
                    List<Credential> creds = new ArrayList<>();
                    Credential cred = pMgr.load(home.resolve("credentials/cred_user_Tiemen.xml"));
                    cred.setSecret(pMgr.load(home.resolve("credentials/secret_Tiemen.xml")));
                    creds.add(cred);
                    Attribute att = cred.getAttribute("Name");
                    if (att != null) {
                        System.out.println(att.getLabel() + " : " + att.getValue());
                    }
                    //INITIALIZE CONNECTION
                    SSLParameters authParam = pMgr.load(home.resolve("app_data/verificationConnection-ssl.param"));
                    Connection conn = cmgr.getConnection(authParam);

                    //RECEIVE THE POLICY
                    Policy pol = spMgr.parsePolicy((String) conn.receive());
                    //System.out.println("Policy: " + pol.getStringRepresentation());
                    //RECEIVE THE NONCE
                    Nonce nonce = (Nonce) conn.receive();
                    System.out.println("Nonce: " + nonce.toString());
                    //CREATE CLAIM
                    pol.initialize(creds);
                    if (pol.getCredentialClaims().isEmpty()) {
                        System.out.println("Can not satisfy claim");
                    } else {
                        Proof proof = cMan.generateProof(pol.getClaim(), nonce);
                        System.out.println("Proof is valid: " + proof.isValid());
                        System.out.println("Proof satistfies policy: " + proof.satisfiesPolicy(pol));
                        conn.send(cMan.serializeProof(proof));
                    }
                    conn.close();*/

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });




    }


    public void requestCredential(final String name) {
        (new Runnable() {
            public void run() {
                try {
                    if (CommunicationHandler.requestCredential(name)) {
                        showToast("Account successfully created");
                    }
                    else {
                        showToast("Error creating account");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).run();
    }


    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run()
            {
                Toast.makeText(RolesActivity.this, toast, Toast.LENGTH_LONG).show();
            }
        });
    }



}
