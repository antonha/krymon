package krymon;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.*;

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
        refreshServices();

        this.swipeRefreshLayout = findViewById(R.id.list_services_refresh_layout);
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
        List<Map<String, String>> dataForView = new ArrayList<>();
        List<Service> services = response.body().getServices();
        Collections.sort(services, new Comparator<Service>() {
            @Override
            public int compare(Service o1, Service o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (Service service : services) {
            Map<String, String> data = serviceAsMap(service);
            dataForView.add(data);
        }
        SimpleAdapter adapter = new SimpleAdapter(ListServicesActivity.this,
                dataForView,
                R.layout.service_list_item,
                new String[]{ITEM_DATA_NAME, ITEM_DATA_STATUS, ITEM_DATA_LAST_CHECKED, ITEM_DATA_URL},
                new int[]{R.id.service_list_item_name, R.id.service_list_item_status, R.id.service_list_item_last_checked, R.id.service_list_item_url}
        );
        ListView backendListView = findViewById(R.id.service_list);
        backendListView.setAdapter(adapter);
    }

    private Map<String, String> serviceAsMap(Service service) {
        Map<String, String> data = new HashMap<>();
        data.put(ITEM_DATA_NAME, service.getName());
        data.put(ITEM_DATA_STATUS, service.getStatus().toString());
        data.put(ITEM_DATA_LAST_CHECKED, service.getLastCheck().toString("yyyy-MM-dd HH:mm:ss"));
        data.put(ITEM_DATA_URL, service.getUrl());
        return data;
    }


}
