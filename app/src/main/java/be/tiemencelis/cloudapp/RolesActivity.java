package be.tiemencelis.cloudapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import be.tiemencelis.beans.FileMeta;


public class RolesActivity extends AppCompatActivity {
    private String[] roles = {"Tiemen", "Teeman", "Rol 3" };

    public RolesActivity() {
        //TODO roles inlezen
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roles);

        ListView list = (ListView) findViewById(R.id.list);

        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, roles));

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO root folder opvragen met gekozen rol
                ArrayList<FileMeta> files = new ArrayList<FileMeta>();
                FileBrowserActivity activity;
                Bundle b;
                Intent i;

                switch (position) {
                    case 0:
                        files.add(new FileMeta("Folder 1", System.currentTimeMillis()));
                        files.add(new FileMeta("File c", 45456, System.currentTimeMillis()));
                        files.add(new FileMeta("Folder 2", System.currentTimeMillis()));
                        files.add(new FileMeta("File a", 1564454, System.currentTimeMillis()));
                        files.add(new FileMeta("File b", 315644, System.currentTimeMillis()));

                        b = new Bundle();
                        b.putString("location", "/");
                        b.putString("role", roles[position]);
                        b.putSerializable("files", files);
                        i = new Intent(RolesActivity.this, FileBrowserActivity.class);
                        i.putExtras(b);
                        startActivity(i);
                        break;
                    case 1:
                        files.add(new FileMeta("Folder 2", System.currentTimeMillis()));
                        files.add(new FileMeta("File a", 1564454, System.currentTimeMillis()));
                        files.add(new FileMeta("File b", 315644, System.currentTimeMillis()));

                        b = new Bundle();
                        b.putString("location", "/");
                        b.putString("role", roles[position]);
                        b.putSerializable("files", files);
                        i = new Intent(RolesActivity.this, FileBrowserActivity.class);
                        i.putExtras(b);
                        startActivity(i);
                        break;
                    case 2:
                        files.add(new FileMeta("Folder 1", System.currentTimeMillis()));
                        files.add(new FileMeta("File c", 45456, System.currentTimeMillis()));

                        b = new Bundle();
                        b.putString("location", "/");
                        b.putString("role", roles[position]);
                        b.putSerializable("files", files);
                        i = new Intent(RolesActivity.this, FileBrowserActivity.class);
                        i.putExtras(b);
                        startActivity(i);
                        break;
                }
            }
        });

    }



}
