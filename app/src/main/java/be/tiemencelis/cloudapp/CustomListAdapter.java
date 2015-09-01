package be.tiemencelis.cloudapp;

/**
 * Created by Tiemen on 12-5-2015.
 * Handler for filling the listview of the filebrowseractivity with custom made items (mylist.xml)
 */

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.List;

import be.tiemencelis.beans.FileMeta;

public class CustomListAdapter extends ArrayAdapter<FileMeta> {

    private final Activity context;
    private List<FileMeta> items;

    public CustomListAdapter(Activity context, List<FileMeta> items) {
        super(context, R.layout.mylist, items);
        this.context=context;
        this.items = items;
    }

    public View getView(int position,View view,ViewGroup parent) {
        LayoutInflater inflater=context.getLayoutInflater();
        View rowView=inflater.inflate(R.layout.mylist, null,true);

        TextView txtTitle = (TextView) rowView.findViewById(R.id.name);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
        TextView size = (TextView) rowView.findViewById(R.id.size);
        TextView modified = (TextView) rowView.findViewById(R.id.modified);

        txtTitle.setText(items.get(position).getName());
        modified.setText(SimpleDateFormat.getDateTimeInstance().format(items.get(position).getLastModified()));
        if (items.get(position).isDirectory()) {
            imageView.setImageResource(R.drawable.folder);
            size.setText("");
        }
        else {
            imageView.setImageResource(R.drawable.file);
            size.setText("Size: " + items.get(position).getSize() / 1024 + " kB");
        }

        return rowView;

    }
}