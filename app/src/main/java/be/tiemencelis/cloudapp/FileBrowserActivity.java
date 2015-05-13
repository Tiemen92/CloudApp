package be.tiemencelis.cloudapp;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

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
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub
                System.out.println("Clicked: \"" + location + files.get(position).getName() + "\" with role " + role);
                /*PersistenceManager pman = Priman.getInstance().getPersistenceManager();

                try {
                    URI home = (new File("/sdcard/CloudApp/")).toURI();
                    SSLParameters param = pman.load(home.resolve("cloudConnection-ssl.param"));
                    Connection conn = Priman.getInstance().getConnectionManager().getConnection(param);
                    conn.send("Hoi");
                    conn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
            }
        });
    }

}
