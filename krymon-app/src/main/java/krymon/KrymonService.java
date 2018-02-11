package krymon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface KrymonService {
    @GET("service")
    Call<ServiceList> listServices();

    @POST("service")
    Call<Void> addService(@Body NewService newService);

    class Builder {
        private static ObjectMapper objectMapper = new ObjectMapper();

        static {
            objectMapper.registerModule(new JodaModule());
        }

        public static KrymonService build(String url) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                    .build();
            return retrofit.create(KrymonService.class);
        }
    }
}
