package krymon;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddServiceActivity extends AppCompatActivity {

    public static final String BACKEND = "backend";
    private String backendUrl;
    private KrymonService krymonService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_service);

        this.backendUrl = getIntent().getStringExtra(BACKEND);
        this.krymonService = KrymonService.Builder.build(backendUrl);

        Spinner httpHttpsSpinner = findViewById(R.id.add_service_protocol_spinner);
        httpHttpsSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"http", "https"}));
        findViewById(R.id.add_service_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AddServiceActivity.this, ListServicesActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.add_service_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String name = ((EditText)findViewById(R.id.add_service_name)).getText().toString();
                String protocol = ((Spinner) findViewById(R.id.add_service_protocol_spinner)).getSelectedItem().toString();
                final String urlEntered = ((EditText)findViewById(R.id.add_service_url_text)).getText().toString();
                Log.i("AddServiceActivity", "Adding new service with name " + name + " and url " + urlEntered + " to " + backendUrl);
                krymonService.addService(new NewService(name, protocol + "://" + urlEntered)).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        Log.i("AddServiceActivity", "Successfully added service with name " + name + " and url " + urlEntered + " to " + backendUrl);
                        Intent intent = new Intent(AddServiceActivity.this, ListServicesActivity.class);
                        intent.putExtra(ListServicesActivity.BACKEND, backendUrl);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable throwable) {
                        Log.e("AddServiceActivity", "Failed to add new service with name " + name + " and url " + urlEntered + " to " + backendUrl, throwable);
                    }
                });

            }
        });

    }
}
