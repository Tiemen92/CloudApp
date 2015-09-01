package be.tiemencelis.cloudapp;

/**
 * Created by Tiemen on 26-8-2015.
 * Activity for showing the different policies in a PolicySet
 */

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import be.tiemencelis.accesspolicy.Policy;
import be.tiemencelis.accesspolicy.PolicySet;


public class PolicySetActivity extends AppCompatActivity {
    private PolicySet policySet;
    private String role;
    private String location;
    private Button save;
    private Button cancel;
    private ListView list;
    private Spinner resolverSpinner;
    private Switch combineSwitch;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy_set);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Bundle b = getIntent().getExtras();
        location = b.getString("location");
        role = b.getString("role");
        policySet = (PolicySet) b.getSerializable("policyset");

        String[] policyNames = new String[policySet.getPolicies().size()];
        for (int i = 0; i < policySet.getPolicies().size(); i++) {
            policyNames[i] = new String("Policy " + (i+1));
        }

        /*Fill list*/
        list = (ListView) findViewById(R.id.policyitems);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, policyNames);
        list.setAdapter(adapter);

        /*Set resolve spinner*/
        resolverSpinner = (Spinner) findViewById(R.id.resolver_type_input);
        switch (policySet.getEffectResolver()) {
            case "deny-overrules":
                resolverSpinner.setSelection(0);
                break;
            case "allow-overrules":
                resolverSpinner.setSelection(1);
                break;
            case "amount-overrules":
                resolverSpinner.setSelection(2);
                break;
            default:
                resolverSpinner.setSelection(0);
                break;
        }

        /*Set combine logic switch*/
        combineSwitch = (Switch) findViewById(R.id.combine_switch);
        if (policySet.getCombineLogic().equals("or")) {
            combineSwitch.setChecked(false);
        } else {
            combineSwitch.setChecked(true);
        }

        /*Open selected policy in new activity*/
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bundle b = new Bundle();
                b.putSerializable("policy", policySet.getPolicy(position));
                b.putInt("id", position);
                Intent i = new Intent(PolicySetActivity.this, PolicyActivity.class);
                i.putExtras(b);
                startActivityForResult(i, 0);
            }
        });

        /*Save button*/
        save = (Button) findViewById(R.id.save_policyset);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*Update combineLogic value*/
                if (combineSwitch.isChecked()) {
                    policySet.setCombineLogic("and");
                } else {
                    policySet.setCombineLogic("or");
                }
                /*Update effectResolver value*/
                switch (resolverSpinner.getSelectedItemPosition()) {
                    case 0:
                        policySet.setEffectResolver("deny-overrules");
                        break;
                    case 1:
                        policySet.setEffectResolver("allow-overrules");
                        break;
                    case 2:
                        policySet.setEffectResolver("amount-overrules");
                        break;
                    default:
                        break;
                }
                /*Push new PolicySet to the server to save*/
                new Thread(new SavePolicy()).start();
                onBackPressed();
            }
        });

        /*Cancel edit, discard changes*/
        cancel = (Button) findViewById(R.id.cancel_policyset);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

    }


    /**
     * Save all changes made to the PolicySet and containing Policies by pushing to the server
     */
    class SavePolicy implements Runnable {
        @Override
        public void run() {
            try {
                if (CommunicationHandler.savePolicySet(role, location, policySet)) {
                    showToast("Policy successfully saved");
                } else {
                    showToast("Error when saving policy");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Receive changes from a PolicyActivity and save them in the PolicySet
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 0:
                if (resultCode == Activity.RESULT_OK) {
                    Bundle b = data.getExtras();
                    policySet.setPolicy((Policy) b.getSerializable("policy"), b.getInt("id"));
                }
        }
    }

    /**
     * Show a toast message
     * @param toast message
     * @param toast message
     */
    public void showToast(final String toast) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(PolicySetActivity.this, toast, Toast.LENGTH_LONG).show();
            }
        });
    }
}
