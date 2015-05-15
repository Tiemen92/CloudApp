package be.tiemencelis.cloudapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

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
        setTitle("File browser: " + location); //TODO enkel mapnaam zetten, of scrollen?

        ListView list = (ListView) findViewById(R.id.list);
        list.setAdapter(new CustomListAdapter(this, files));

        list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                System.out.println("Clicked: \"" + location + files.get(position).getName() + "\" with role " + role);

                /*Load directory content and launch new intent*/
                if (files.get(position).isDirectory()) {
                    new Thread(new LoadDirContents(position)).start();
                }
                /*Load file content, save it and open it*/
                else {
                    new Thread(new LoadFileContents(position)).start();
                }

            }
        });
    }


    /**
     * Load dir contents and launch new activity with it
     */
    class LoadDirContents implements Runnable {
        int position;
        LoadDirContents(int position) {this.position = position;}
        @Override
        public void run() {
            ArrayList<FileMeta> newFiles = null;
            Bundle b;
            Intent i;
            try {
                newFiles = CommunicationHandler.requestDirectoryContents(role, location + files.get(position).getName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (newFiles == null) {
                Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_LONG).show();
            } else {
                b = new Bundle();
                b.putString("location", location + files.get(position).getName() + "/");
                b.putString("role", role);
                b.putSerializable("files", newFiles);
                i = new Intent(FileBrowserActivity.this, FileBrowserActivity.class);
                i.putExtras(b);
                startActivity(i);
            }
        }
    }


    /**
     * Load file content and save it to /sdcard/CloudApp/
     */
    class LoadFileContents implements Runnable {
        int position;
        LoadFileContents(int position) {this.position = position;}
        @Override
        public void run() {
            byte[] content = null;
            try {
                content = CommunicationHandler.requestFileContents(role, location + files.get(position).getName());

                if (content == null) {
                    Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_LONG).show();
                } else {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("/sdcard/CloudApp/" + files.get(position).getName()));
                    bos.write(content);
                    bos.flush();
                    bos.close();
                    //TODO open file na save
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
