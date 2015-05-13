package be.tiemencelis.cloudapp;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;

import be.kuleuven.cs.priman.Priman;
import be.kuleuven.cs.priman.connection.Connection;
import be.kuleuven.cs.priman.manager.PersistenceManager;
import be.kuleuven.cs.primanprovider.connection.ssl.SSLParameters;
import be.tiemencelis.beans.FileMeta;


public class FileBrowserActivity extends AppCompatActivity {
    private ArrayList<FileMeta> files;
    private String location;
    private String role;


    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        Bundle b = getIntent().getExtras();
        location = b.getString("location");
        role = b.getString("role");
        files = (ArrayList<FileMeta>) b.getSerializable("files");

        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(new CustomListAdapter(this, location, files));

        list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                // TODO Auto-generated method stub
                System.out.println("Clicked: \"" + location + files.get(position).getName() + "\" with role " + role);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<FileMeta> newFiles = null; //TODO check of op file of dir is geklikt!!!!!
                        Bundle b;
                        Intent i;
                        try {
                            newFiles = CommunicationHandler.requestDirectoryContents(role, location + files.get(position).getName()); //TODO of req file
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (newFiles == null) {
                            Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_LONG).show();
                        } else {
                            b = new Bundle();
                            b.putString("location", location + newFiles.get(position).getName() + "/");
                            b.putString("role", role);
                            b.putSerializable("files", newFiles);
                            i = new Intent(FileBrowserActivity.this, FileBrowserActivity.class); //TODO of open file
                            i.putExtras(b);
                            startActivity(i);
                        }
                    }
                });

            }
        });
    }

}
