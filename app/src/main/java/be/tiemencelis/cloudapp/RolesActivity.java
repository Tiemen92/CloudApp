package be.tiemencelis.cloudapp;

import android.app.ListActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

import be.tiemencelis.accesspolicy.Policy;
import be.tiemencelis.beans.ConnectInfo;
import be.tiemencelis.security.SecurityHandler;

//import be.tiemencelis.beans.PolicyUpdate;


public class RolesActivity extends AppCompatActivity {
    String[] itemname ={
                        "Safari",
                        "Camera",
                        "Global",
                        "FireFox",
                        "UC Browser",
                        "Android Folder",
                        "VLC Player",
                        "Cold War",
                        "Windows",
                        "Linux",
                        "Macshit" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roles);
        Policy pol;
        ListView list = (ListView) findViewById(R.id.list);

        list.setAdapter(new ArrayAdapter<String>(
                this, R.layout.mylist,
                R.id.Itemname, itemname));

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
