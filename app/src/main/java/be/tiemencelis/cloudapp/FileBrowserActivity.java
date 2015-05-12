package be.tiemencelis.cloudapp;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import be.tiemencelis.beans.FileMeta;


public class FileBrowserActivity extends AppCompatActivity {
    private ArrayList<FileMeta> files;
    private String location;
    private String role;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        Bundle b = getIntent().getExtras();
        location = b.getString("location");
        role = b.getString("role");
        files = (ArrayList<FileMeta>) b.getSerializable("files");

        ListView list = (ListView) findViewById(R.id.list);
        /*String folder = "";
        List<FileMeta> files = new ArrayList<FileMeta>();
        files.add(new FileMeta("Folder 1", System.currentTimeMillis()));
        files.add(new FileMeta("File c", 45456, System.currentTimeMillis()));
        files.add(new FileMeta("Folder 2", System.currentTimeMillis()));
        files.add(new FileMeta("File a", 1564454, System.currentTimeMillis()));
        files.add(new FileMeta("File b", 315644, System.currentTimeMillis()));*/

        list.setAdapter(new CustomListAdapter(this, location, files));

        list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub
                System.out.println("Clicked: \"" + location + files.get(position).getName() + "\" with role " + role);
            }
        });
    }

}
