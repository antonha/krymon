package krymon;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

public class AddBackendActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_backend);
        Spinner httpHttpsSpinner = findViewById(R.id.add_backend_protocol_spinner);
        httpHttpsSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"http", "https"}));
        findViewById(R.id.add_backend_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AddBackendActivity.this, ListBackendsActivity.class);
                startActivity(intent);
            }
        });
        findViewById(R.id.add_backend_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AddBackendActivity.this, ListBackendsActivity.class);
                String protocol = ((Spinner) findViewById(R.id.add_backend_protocol_spinner)).getSelectedItem().toString();
                String urlEntered = ((EditText)findViewById(R.id.add_backend_url_text)).getText().toString();
                intent.putExtra(ListBackendsActivity.NEW_BACKEND, protocol + "://" + urlEntered);
                startActivity(intent);
            }
        });

    }
}
