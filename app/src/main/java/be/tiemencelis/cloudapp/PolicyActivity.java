package be.tiemencelis.cloudapp;

/**
 * Created by Tiemen on 26-8-2015.
 * Activity for showing the different items in a Policy
 */

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;

import java.util.List;
import java.util.UUID;

import be.tiemencelis.accesspolicy.Operation;
import be.tiemencelis.accesspolicy.Policy;
import be.tiemencelis.accesspolicy.RequirementData;
import be.tiemencelis.accesspolicy.RequirementItem;


public class PolicyActivity extends AppCompatActivity {
    private Policy policy;
    private int id;
    private ListView list;
    private Switch effect;
    private EditText minimum;
    private EditText age;
    private String[] itemTypes;
    private String[] contextTypes = {"location", "ntp", "connected-wlan", "paired-bluetooth", "nfc-id", "user-role-nearby-wlan"};
    private String[] op_location = {"is-in-city", "within-meters", "equals-cell-id"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy);

        Bundle b = getIntent().getExtras();
        policy = (Policy) b.getSerializable("policy");
        id = b.getInt("id");

        itemTypes = getResources().getStringArray(R.array.item_types);

        effect = (Switch) findViewById(R.id.effect_switch);
        if (policy.getEffect().equals("allow")) {
            effect.setChecked(true);
        } else {
            effect.setChecked(false);
        }
        minimum = (EditText) findViewById(R.id.minimum_input);
        minimum.setText(policy.getMinimum().toString());
        age = (EditText) findViewById(R.id.age_input);
        age.setText(policy.getAge());

        list = (ListView) findViewById(R.id.requirement_item_list);
        list.setAdapter(new PolicyListAdapter(this, policy.getRequirementItems()));
    }

    /**
     * Load all data of all the items, build a new Policy item with it
     * and return it to the previous activity (PolicySetActivity) to save the changes
     */
    @Override
    public void onBackPressed() {
        /*Save changes*/
        Policy newPol = new Policy();

        newPol.setId(UUID.randomUUID());
        newPol.setAge(age.getText().toString());
        if (effect.isChecked()) {
            newPol.setEffect("allow");
        } else {
            newPol.setEffect("deny");
        }
        newPol.setMinimum(Integer.parseInt(minimum.getText().toString()));

        /*Handle every element (RequirementItem) separately and read all the values (spinners, switches, edittexts)*/
        for (int i = 0; i < list.getCount(); i++) {
            RequirementItem item = new RequirementItem();
            View v = list.getChildAt(i);

            item.setId(UUID.randomUUID());
            item.setType(getResources().getStringArray(R.array.item_types)[((Spinner) v.findViewById(R.id.item_type_input)).getSelectedItemPosition()]);

            RequirementData data = new RequirementData();
            data.setDataType(contextTypes[((Spinner) v.findViewById(R.id.context_type_input)).getSelectedItemPosition()]);
            data.setMinTrust(((Spinner) v.findViewById(R.id.trust_input)).getSelectedItemPosition() + 1);
            item.setData(data);

            Operation operation = new Operation();
            switch (data.getDataType()) {
                case "location":
                    operation.setName(op_location[((Spinner) v.findViewById(R.id.operation_type_input)).getSelectedItemPosition()]);
                    break;
                case "ntp":
                    operation.setName(getResources().getStringArray(R.array.op_ntp)[((Spinner) v.findViewById(R.id.operation_type_input)).getSelectedItemPosition()]);
                    break;
                case "connected-wlan":
                    operation.setName(getResources().getStringArray(R.array.op_connected_wlan)[((Spinner) v.findViewById(R.id.operation_type_input)).getSelectedItemPosition()]);
                    break;
                case "paired-bluetooth":
                    operation.setName(getResources().getStringArray(R.array.op_paired_bluetooth)[((Spinner) v.findViewById(R.id.operation_type_input)).getSelectedItemPosition()]);
                    break;
                case "nfc-id":
                    operation.setName(getResources().getStringArray(R.array.op_nfc_id)[((Spinner) v.findViewById(R.id.operation_type_input)).getSelectedItemPosition()]);
                    break;
                case "user-role-nearby-wlan":
                    operation.setName(getResources().getStringArray(R.array.op_user_role_nearby_wlan)[((Spinner) v.findViewById(R.id.operation_type_input)).getSelectedItemPosition()]);
                    break;
            }
            operation.setArguments(((EditText) v.findViewById(R.id.arguments_input)).getText().toString());
            EditText value1 = (EditText) v.findViewById(R.id.value1_input);
            EditText value2 = (EditText) v.findViewById(R.id.value2_input);
            if (!value1.getText().toString().isEmpty()) {
                operation.addValue(value1.getText().toString());
            }
            if (!value2.getText().toString().isEmpty()) {
                operation.addValue(value2.getText().toString());
            }
            item.setOperation(operation);

            newPol.addRequirementItem(item);
        }

        /*Send new Policy to previous activity*/
        Bundle bundle = new Bundle();
        bundle.putSerializable("policy", newPol);
        bundle.putInt("id", id);
        Intent intent = new Intent();
        intent.putExtras(bundle);
        setResult(RESULT_OK, intent);

        super.onBackPressed();
    }

    /**
     * Custom adapter to fill list with items
     */
    public class PolicyListAdapter extends ArrayAdapter<RequirementItem> {

        private final Activity context;
        private List<RequirementItem> items;

        public PolicyListAdapter(Activity context, List<RequirementItem> items) {
            super(context, R.layout.mylist, items);
            this.context=context;
            this.items = items;
        }

        /**
         * Fill a single item (RequirementItem) from the list
         * @param position
         * @param view
         * @param parent
         * @return
         */
        public View getView(int position,View view,ViewGroup parent) {
            LayoutInflater inflater=context.getLayoutInflater();
            View rowView=inflater.inflate(R.layout.requirement_item, null, true);

            Spinner itemType = (Spinner) rowView.findViewById(R.id.item_type_input);
            Spinner contextType = (Spinner) rowView.findViewById(R.id.context_type_input);
            Spinner trustType = (Spinner) rowView.findViewById(R.id.trust_input);
            final Spinner operationType = (Spinner) rowView.findViewById(R.id.operation_type_input);
            EditText arguments = (EditText) rowView.findViewById(R.id.arguments_input);
            EditText value1 = (EditText) rowView.findViewById(R.id.value1_input);
            EditText value2 = (EditText) rowView.findViewById(R.id.value2_input);

            switch (items.get(position).getType()) {
                case "context":
                    itemType.setSelection(0);
                    break;
                case "idmx":
                    itemType.setSelection(1);
                    break;
                case "x509":
                    itemType.setSelection(2);
                    break;
                default:
                    break;
            }

            if (!items.get(position).getType().equals("context")) {
                rowView.findViewById(R.id.requirement_data).setVisibility(View.GONE);
                rowView.findViewById(R.id.operation_data).setVisibility(View.GONE);
                rowView.findViewById(R.id.values_data).setVisibility(View.GONE);
                return rowView;
            }

            String[] ops;
            contextType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, contextTypes));

            switch (items.get(position).getData().getDataType()) {
                case "location":
                    contextType.setSelection(0);
                    operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, op_location));
                    for (int i = 0; i < op_location.length; i++) {
                        if (items.get(position).getOperation().getName().equals(op_location[i])) {
                            operationType.setSelection(i, false);
                            break;
                        }
                    }
                    break;
                case "ntp":
                    contextType.setSelection(1);
                    ops = getResources().getStringArray(R.array.op_ntp);
                    operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, ops));
                    for (int i = 0; i < ops.length; i++) {
                        if (items.get(position).getOperation().getName().equals(ops[i])) {
                            operationType.setSelection(i, false);
                            break;
                        }
                    }
                    break;
                case "connected-wlan":
                    contextType.setSelection(2);
                    operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_connected_wlan)));
                    operationType.setSelection(0, false);
                    break;
                case "paired-bluetooth":
                    contextType.setSelection(3);
                    operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_paired_bluetooth)));
                    operationType.setSelection(0, false);
                    break;
                case "nfc-id":
                    contextType.setSelection(4);
                    operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_nfc_id)));
                    operationType.setSelection(0, false);
                    break;
                case "user-role-nearby-wlan":
                    contextType.setSelection(5);
                    operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_user_role_nearby_wlan)));
                    operationType.setSelection(0, false);
                    break;
                default:
                    break;
            }

            /*Update operation spinner items when context type changes*/
            contextType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean stop = true;
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (stop) { /*Stop initial invoke*/
                        stop = false;
                        return;
                    }
                    switch (position) {
                        case 0:
                            operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, op_location));
                            break;
                        case 1:
                            operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_ntp)));
                            break;
                        case 2:
                            operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_connected_wlan)));
                            break;
                        case 3:
                            operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_paired_bluetooth)));
                            break;
                        case 4:
                            operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_nfc_id)));
                            break;
                        case 5:
                            operationType.setAdapter(new ArrayAdapter<String>(PolicyActivity.this, android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.op_user_role_nearby_wlan)));
                            break;

                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            trustType.setSelection(items.get(position).getData().getMinTrust() - 1);

            arguments.setText(items.get(position).getOperation().getArguments());

            if (!items.get(position).getOperation().getValues().isEmpty()) {
                value1.setText(items.get(position).getOperation().getValue(0));
            }
            if (items.get(position).getOperation().getValues().size() > 1) {
                value2.setText(items.get(position).getOperation().getValue(1));
            }

            return rowView;
        }
    }
}
