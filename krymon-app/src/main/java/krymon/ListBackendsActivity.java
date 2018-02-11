package krymon;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.HashSet;
import java.util.Set;

public class ListBackendsActivity extends AppCompatActivity {

    public static final String NEW_BACKEND = "new_backend";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String newBackend = getIntent().getStringExtra(NEW_BACKEND);
        SharedPreferences settings = getSharedPreferences("krymon", 0);
        Set<String> backends = settings.getStringSet("backends", new HashSet<String>());
        if (newBackend != null) {
            backends.add(newBackend);
            SharedPreferences.Editor edit = settings.edit();
            edit.putStringSet("backends", backends);
            edit.commit();
        }
        super.onCreate(savedInstanceState);
        setContentView(krymon.R.layout.activity_list_backends);
        Toolbar toolbar = findViewById(krymon.R.id.toolbar);
        setSupportActionBar(toolbar);
        renderBackends(backends);
        this.<FloatingActionButton>findViewById(R.id.list_services_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ListBackendsActivity.this, AddBackendActivity.class);
                startActivity(intent);
            }
        });
    }

    private void renderBackends(Set<String> backends) {
        ListView backendListView = findViewById(R.id.backend_list);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                backends.toArray(new String[backends.size()])
        );
        backendListView.setAdapter(adapter);
        backendListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = adapter.getItem(position);
                Intent intent = new Intent(ListBackendsActivity.this, ListServicesActivity.class);
                intent.putExtra(ListServicesActivity.BACKEND, item);
                startActivity(intent);
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(krymon.R.menu.menu_list_backends, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == krymon.R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
