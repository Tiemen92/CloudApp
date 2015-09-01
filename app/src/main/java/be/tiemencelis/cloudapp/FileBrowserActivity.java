package be.tiemencelis.cloudapp;

/**
 * Created by Tiemen on 12-5-2015.
 * Activity for showing files and directories
 */

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import be.tiemencelis.accesspolicy.PolicySet;
import be.tiemencelis.beans.FileMeta;


public class FileBrowserActivity extends AppCompatActivity {
    private ArrayList<FileMeta> files;
    private String location;
    private String role;
    private ListView list;


    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        /*Load data to show*/
        Bundle b = getIntent().getExtras();
        location = b.getString("location");
        role = b.getString("role");
        files = (ArrayList<FileMeta>) b.getSerializable("files");
        setTitle("File browser: " + location);

        list = (ListView) findViewById(R.id.list);
        list.setAdapter(new CustomListAdapter(this, files));
        registerForContextMenu(list);

        /*onClick event on listitem (file or dir)*/
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

        /*onClick event for the share button*/
        Button shareButton = (Button) findViewById(R.id.bShare); /*TODO new policies ipv copy*/
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater inflater = getLayoutInflater();
                final View layout = inflater.inflate(R.layout.input_dialog, (ViewGroup) findViewById(R.id.layout_root));
                final EditText input = (EditText) layout.findViewById(R.id.editTextDialog);
                input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());

                /*Create an alert and ask for necessary information for sharing the data*/
                new AlertDialog.Builder(FileBrowserActivity.this)
                        .setTitle("Share folder")
                        .setMessage("Enter role/username to share with:")
                        .setView(layout)
                        .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                shareData(input.getEditableText().toString());
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                showToast("Share folder canceled");
                                dialog.cancel();
                            }
                        })
                        .create().show();
                layout.findViewById(R.id.checkboxes).setVisibility(View.GONE);
            }
        });
    }


    /**
     * Context menu when long press on a file/dir item
     * @param menu
     * @param v
     * @param menuInfo
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.contextual_menu, menu);
    }

    /**
     * Handle selected item from context menu (delete and update not yet supported)
     * @param item
     * @return
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.id_delete:
                //TODO delete file
                break;
            case R.id.id_edit_policy:
                new Thread(new LoadPolicy(info.position, "w")).start();
                break;
            case R.id.id_get_policy:
                new Thread(new LoadPolicy(info.position, "r")).start();
                break;
            case R.id.id_update_file:
                //TODO select new file or change dir name
                break;
            default:
                break;
        }
        return super.onContextItemSelected(item);
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
                newFiles = CommunicationHandler.requestDirectoryContents(role, location + files.get(position).getName() + "/");
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*No access rights received for this directory*/
            if (newFiles == null) {
                showToast("Authentication failed");
            }
            /*Contents received, launch new activity to show them*/
            else {
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
     * Load file content and save it to /sdcard/CloudApp/data/
     */
    class LoadFileContents implements Runnable {
        int position;
        LoadFileContents(int position) {this.position = position;}
        @Override
        public void run() {
            byte[] content = null;
            try {
                content = CommunicationHandler.requestFileContents(role, location + files.get(position).getName());

                /*No access rights received for this file*/
                if (content == null) {
                    showToast("Authentication failed");
                }
                /*File received, launch it with the appropriate application*/
                else {
                    /*Save received file*/
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("/sdcard/CloudApp/data/" + files.get(position).getName()));
                    bos.write(content);
                    bos.flush();
                    bos.close();

                    /*Launch app for file type or chooser with possible apps to open the file*/
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    Intent editIntent = new Intent(Intent.ACTION_EDIT);
                    MimeTypeMap myMime = MimeTypeMap.getSingleton();
                    String extension = MimeTypeMap.getFileExtensionFromUrl(files.get(position).getName());
                    if (extension == null) {
                        System.out.println("No extension found in: " + files.get(position).getName());
                        throw new Exception("Extension not found");
                    }
                    System.out.println("Extension: " + extension);
                    String mime = myMime.getMimeTypeFromExtension(extension);
                    if (mime == null) {
                        System.out.println("No Mime/type found for extension " + extension);
                        mime = "text/*";
                    }
                    System.out.println("MIME/type: " + mime);
                    editIntent.setDataAndType(Uri.fromFile(new File("/sdcard/CloudApp/data/" + files.get(position).getName())), mime);
                    viewIntent.setDataAndType(Uri.fromFile(new File("/sdcard/CloudApp/data/" + files.get(position).getName())), mime);
                    Intent j = Intent.createChooser(viewIntent, "Open file with:");
                    j.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { editIntent });
                    startActivity(j);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Load policyset and launch new activity with it to possibly edit them
     */
    class LoadPolicy implements Runnable {
        int position;
        String action;
        LoadPolicy(int position, String action) {this.position = position; this.action = action;}
        @Override
        public void run() {
            PolicySet policySet = null;
            Bundle b;
            Intent i;
            String loc = null;
            try {
                /*Create location path based on type (dir or file)*/
                if (files.get(position).isDirectory()) {
                    loc = new String(location + files.get(position).getName() + "/");
                } else {
                    loc = new String(location + files.get(position).getName());
                }
                policySet = CommunicationHandler.requestPolicySet(role, loc, action);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*access rights received for the requested policy*/
            if (policySet == null) {
                showToast("Authentication failed");
            }
            /*Policy received, laucnh activity to show it*/
            else {
                b = new Bundle();
                b.putString("location", loc);
                b.putString("role", role);
                b.putSerializable("policyset", policySet);
                i = new Intent(FileBrowserActivity.this, PolicySetActivity.class);
                i.putExtras(b);
                startActivity(i);
            }
        }
    }


    /**
     * Handle the sharing of data with another user
     * @param shareRole user to share with
     */
    public void shareData(final String shareRole) {
        (new Runnable() {
            public void run() {
                try {
                    CommunicationHandler.shareData(role, shareRole, location);
                    showToast("Folder successfully shared with " + shareRole);
                } catch (Exception e) {
                    showToast("Error sharing folder with " + shareRole);
                    e.printStackTrace();
                }
            }
        }).run();
    }


    /**
     * Show toast message
     * @param toast message
     */
    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run()
            {
                Toast.makeText(FileBrowserActivity.this, toast, Toast.LENGTH_LONG).show();
            }
        });
    }

}
