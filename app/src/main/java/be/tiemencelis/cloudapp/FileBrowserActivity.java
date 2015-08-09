package be.tiemencelis.cloudapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
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
        setTitle("File browser: " + location);

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

        Button shareButton = (Button) findViewById(R.id.bShare); /*TODO new policies ipv copy*/
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater inflater = getLayoutInflater();
                final View layout = inflater.inflate(R.layout.input_dialog, (ViewGroup) findViewById(R.id.layout_root));
                final EditText input = (EditText) layout.findViewById(R.id.editTextDialog);
                input.setTransformationMethod(android.text.method.SingleLineTransformationMethod.getInstance());

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

            if (newFiles == null) {
                showToast("Authentication failed");
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

                if (content == null) {
                    showToast("Authentication failed");
                } else {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("/sdcard/CloudApp/data/" + files.get(position).getName()));
                    bos.write(content);
                    bos.flush();
                    bos.close();


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


    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run()
            {
                Toast.makeText(FileBrowserActivity.this, toast, Toast.LENGTH_LONG).show();
            }
        });
    }


}
