package be.tiemencelis.cloudapp;

import android.app.ListActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import be.tiemencelis.beans.FileMeta;


public class RolesActivity extends AppCompatActivity {
    private List<FileMeta> files;
    private String location;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roles);

        ListView list = (ListView) findViewById(R.id.list);
        String folder = "";
        List<FileMeta> files = new ArrayList<FileMeta>();
        files.add(new FileMeta("Folder 1", System.currentTimeMillis()));
        files.add(new FileMeta("File c", 45456, System.currentTimeMillis()));
        files.add(new FileMeta("Folder 2", System.currentTimeMillis()));
        files.add(new FileMeta("File a", 1564454, System.currentTimeMillis()));
        files.add(new FileMeta("File b", 315644, System.currentTimeMillis()));

        list.setAdapter(new CustomListAdapter(this, folder, files));

        list.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub

            }
        });

        //fillList();
    }



    /*private void fillList() {
        ListView list = (ListView) findViewById(R.id.list);

        ArrayList<String> content = new ArrayList<String>();
        content.add("Tiemen");
        content.add("Teeeman");
        content.add("Role 3");
        ArrayAdapter<String> adapter;

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, content);
        list.setAdapter(adapter);

    }*/

}
