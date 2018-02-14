package krymon;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListServicesActivity extends AppCompatActivity {

    public static final String BACKEND = "backend";

    private static final String ITEM_DATA_NAME = "name";
    private static final String ITEM_DATA_STATUS = "status";
    private static final String ITEM_DATA_LAST_CHECKED = "last_checked";
    private static final String ITEM_DATA_URL = "url";

    private String backendUrl;
    private KrymonService krymonService;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_services);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        this.backendUrl = getIntent().getStringExtra(BACKEND);
        krymonService = KrymonService.Builder.build(backendUrl);

        this.swipeRefreshLayout = findViewById(R.id.list_services_refresh_layout);

        refreshServices();
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshServices();
            }
        });

        FloatingActionButton addServiceButton = findViewById(R.id.list_services_add);
        addServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ListServicesActivity.this, AddServiceActivity.class);
                intent.putExtra(AddServiceActivity.BACKEND, backendUrl);
                startActivity(intent);
            }
        });
    }


    private void refreshServices() {
        Log.i("ListServicesActivity", "Asking backend url " + backendUrl);
        swipeRefreshLayout.setRefreshing(true);
        krymonService.listServices().enqueue(new Callback<ServiceList>() {
            @Override
            public void onResponse(Call<ServiceList> call, Response<ServiceList> response) {
                Log.i("ListServicesActivity", "Got response from backend " + backendUrl);
                swipeRefreshLayout.setRefreshing(false);
                renderServices(response);
            }

            @Override
            public void onFailure(Call<ServiceList> call, Throwable throwable) {
                Log.e("ListServicesActivity", "Error when calling backend service.", throwable);
            }
        });
    }

    private void renderServices(Response<ServiceList> response) {
        List<Service> services = response.body().getServices();
        Collections.sort(services, new Comparator<Service>() {
            @Override
            public int compare(Service o1, Service o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        ListView backendListView = findViewById(R.id.service_list);
        backendListView.setAdapter(new ServiceItemAdapter(this, services));
    }

    private class ServiceItemAdapter extends ArrayAdapter<Service> {

        public ServiceItemAdapter(Context context, List<Service> services) {
            super(context, 0, services);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi;
                vi = LayoutInflater.from(getContext());
                v = vi.inflate(R.layout.service_list_item, null);
            }
            final Service service = getItem(position);
            if (service != null) {
                v.<TextView>findViewById(R.id.service_list_item_name).setText(service.getName());
                v.<TextView>findViewById(R.id.service_list_item_url).setText(service.getUrl());
                v.<TextView>findViewById(R.id.service_list_item_status).setText(service.getStatus().toString());
                v.<TextView>findViewById(R.id.service_list_item_last_checked).setText(service.getLastCheck().toString("yyyy-MM-dd HH:mm:ss"));
                v.<Button>findViewById(R.id.service_list_item_button_delete).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        krymonService.deleteService(service.getId()).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                refreshServices();
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable throwable) {
                                refreshServices();
                            }
                        });
                    }
                });
            }
            return v;
        }
    }

}
