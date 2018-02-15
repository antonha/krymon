package krymon;

import com.fasterxml.jackson.datatype.joda.JodaModule;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;

public class Main {
    public static void main(String[] args) {
        Json.mapper.registerModule(new JodaModule());
        Krymon krymon = new Krymon(Vertx.vertx(), parseFile(args), 60_000);
        krymon.start().subscribe();
    }

    private static String parseFile(String[] args) {
        if(args.length > 0){
            return args[0];
        }
        else{
            return "services.json";
        }
    }
}
