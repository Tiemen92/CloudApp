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
import android.widget.CheckBox;
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
    private static List<String> roles;
    private ArrayAdapter<String> adapter;
    private static final URI home = (new File("/sdcard/CloudApp/")).toURI();

    public RolesActivity() {
        LoadRoles();
    }


    public static List<String> getRoles() {
        return roles;
    }


    private void LoadRoles() {
        File credentialFolder = new File("/sdcard/CloudApp/credentials/");
        FilenameFilter credsOnlyFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("cred_user_");
            }
        };
        File[] credentials = credentialFolder.listFiles(credsOnlyFilter);
        List<String> roleNames = new ArrayList<>();
        for (File cred : credentials) {
            roleNames.add(cred.getName().substring(10, cred.getName().length()-4));
        }
        Collections.sort(roleNames);
        roles = roleNames;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roles);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        ListView list = (ListView) findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roles);
        adapter.setNotifyOnChange(true);
        list.setAdapter(adapter);

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
                        files = CommunicationHandler.requestDirectoryContents(roles.get(position), "/");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (files == null) {
                        showToast("Authentication failed");
                    } else {
                        b = new Bundle();
                        b.putString("location", "/");
                        b.putString("role", roles.get(position));
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
                final View layout = inflater.inflate(R.layout.input_dialog, (ViewGroup) findViewById(R.id.layout_root));
                final EditText input = (EditText) layout.findViewById(R.id.editTextDialog);
                input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());

                new AlertDialog.Builder(RolesActivity.this)
                        .setTitle("Create account")
                        .setMessage("Enter your name/role here:")
                        .setView(layout)
                        .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                int admin = 0;
                                boolean credOnly = ((CheckBox) layout.findViewById(R.id.credentialCheckBox)).isChecked();
                                if (((CheckBox) layout.findViewById(R.id.adminCheckBox)).isChecked()) {
                                    admin = 1;
                                }
                                createAccount(input.getEditableText().toString(), credOnly, admin);
                                LoadRoles();
                                adapter.clear();
                                adapter.addAll(roles);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                showToast("Create new account canceled");
                                dialog.cancel();
                            }
                        })
                        .create().show();

                /*TODO check nfc use*/
                //Intent i = new Intent(RolesActivity.this, NfcActivity.class);
                //startActivity(i);
            }
        });
    }



    public void createAccount(final String name, final boolean credentialOnly, final int admin) {
        (new Runnable() {
            public void run() {
                try {
                    if (credentialOnly) {
                        if (CommunicationHandler.requestCredential(name)) {
                            showToast("Credential successfully created");
                        } else {
                            showToast("Error requesting credential");
                        }
                    } else {
                        if (CommunicationHandler.createAccount(name, admin)) {
                            showToast("Account successfully created");
                        } else {
                            showToast("Error creating account");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).run();
    }


    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(RolesActivity.this, toast, Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    protected void onDestroy() {
        ContextManager.tearDown();
        super.onDestroy();
    }



}
