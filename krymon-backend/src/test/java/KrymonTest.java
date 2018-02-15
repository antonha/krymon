import com.fasterxml.jackson.datatype.joda.JodaModule;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import krymon.Krymon;
import krymon.NewService;
import krymon.Service;
import krymon.ServiceList;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import rx.Single;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class KrymonTest {

    private static final long CHECK_PERIOD = 100;
    private Krymon krymon;
    private Vertx vertx;
    private HttpClient httpClient;

    @Before
    public void setup() throws IOException {
        vertx = Vertx.vertx();
        Json.mapper.registerModule(new JodaModule());
        krymon = new Krymon(vertx, tmpFile().getAbsolutePath(), CHECK_PERIOD);
        krymon.start().toBlocking().value();

        httpClient = vertx.createHttpClient();
    }

    private static File tmpFile() throws IOException {
        File file = File.createTempFile("krymon-test", ".json");
        file.delete();
        file.deleteOnExit();
        return file;
    }

    @After
    public void cleanup() {
        for (Service service : getServices().getServices()) {
            delete(service.getId());
        }
        close(vertx).toBlocking().value();
    }

    Single<Void> close(Vertx vertx){
        return Single.create(subscriber -> vertx.close(event -> {
            if(event.succeeded()){
                subscriber.onSuccess(null);
            } else {
                subscriber.onError(event.cause());
            }
        }));
    }

    @Test
    public void testShouldListEmptyBeforeAdding() {
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void shouldBeAbleToAdAndDeleteService() {
        HttpClientResponse postResponse = addService(new NewService("example", "http://www.example.com"));
        ServiceList actual = getServices();
        assertEquals(1, actual.getServices().size());
        Service service = actual.getServices().get(0);
        assertEquals("example", service.getName());
        assertEquals("http://www.example.com", service.getUrl());
        assertEquals(postResponse.getHeader("Location"), "/service/" + service.getId());

        delete(service.getId());
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void shouldBeAbleToAdTwoServices() {
        addService(new NewService("example", "http://www.example.com"));
        assertEquals(1, getServices().getServices().size());

        addService(new NewService("google", "http://www.google.com"));
        assertEquals(2, getServices().getServices().size());

        List<Service> services = getServices().getServices();
        delete(services.get(0).getId());
        assertEquals(1, getServices().getServices().size());

        delete(services.get(1).getId());
        assertEquals(0, getServices().getServices().size());
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void deletingNonExistentServiceShouldReturn404() {
        assertEquals(404, delete("foobar").statusCode());
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void deletingNonExistentServiceWithOtherServiceThereShouldReturn404() {
        addService(new NewService("example", "http://www.example.com"));
        Service service = getServices().getServices().get(0);
        assertEquals(404, delete(service.getId() + "foobar").statusCode());
        assertEquals(200, delete(service.getId()).statusCode());
    }

    @Test
    public void shouldCheckStatusOfDifferentServices() throws InterruptedException {
        HttpServer server1 = startServerWithStatus(200);
        HttpServer server2 = startServerWithStatus(500);
        try{
            addService(new NewService("service1", "http://0.0.0.0:" + server1.actualPort()));

            addService(new NewService("service2", "http://0.0.0.0:" + server2.actualPort()));

            await(() -> {
                List<Service> services = getServices().getServices();
                Service service1 = withName(services, "service1");
                assertEquals(Service.Status.OK, service1.getStatus());
                Service service2 = withName(services, "service2");
                assertEquals(Service.Status.FAIL, service2.getStatus());
            });
        } finally {
            server1.close();
            server2.close();
        }
    }


    @Test
    public void shouldHandleChangingStatus() throws InterruptedException {
        HttpServer server = startServerWithStatus(200);
        try{
            addService(new NewService("server", "http://0.0.0.0:" + server.actualPort()));

            await(() -> {
                Service service = getServices().getServices().get(0);
                assertEquals(Service.Status.OK, service.getStatus());
                assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 2);
            });

            setStatus(server, 500);
            await(() -> {
                Service failingService = getServices().getServices().get(0);
                assertEquals(Service.Status.FAIL, failingService.getStatus());
                assertTrue(Seconds.secondsBetween(failingService.getLastCheck(), DateTime.now()).getSeconds() < 10);
            });

            setStatus(server, 200);
            await(() -> {
                Service onceAgainSucceding = getServices().getServices().get(0);
                assertEquals(Service.Status.OK, onceAgainSucceding.getStatus());
                assertTrue(Seconds.secondsBetween(onceAgainSucceding.getLastCheck(), DateTime.now()).getSeconds() < 10);
            });
        } finally {
            server.close();
        }
    }

    @Test
    public void shouldHandleFoobarPort() throws InterruptedException {
        addService(new NewService("server", "http://0.0.0.0:" + 554215));
        await(() -> {
            Service service = getServices().getServices().get(0);
            assertEquals(Service.Status.FAIL, service.getStatus());
            assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 10);
        });
    }


    @Test
    public void shouldHandleFoobarUrl() throws InterruptedException {
        addService(new NewService("server", "8654###{]+[{}("));
        await(() -> {
            Service service = getServices().getServices().get(0);
            assertEquals(Service.Status.FAIL, service.getStatus());
            assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 10);
        });
    }

    private Service withName(List<Service> services, String name) {
        for (Service s : services) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        return null;
    }

    private void setStatus(HttpServer server, int newStatus) {
        ((SettableStatusRequestHandler) server.requestHandler()).status.set(newStatus);
    }

    private HttpServer startServerWithStatus(int statusCode) {
        return Single.<HttpServer>create(subscriber ->
                vertx.createHttpServer().requestHandler(new SettableStatusRequestHandler(statusCode)).listen(0, httpServer -> {
                    if (httpServer.succeeded()) {
                        subscriber.onSuccess(httpServer.result());
                    } else {
                        subscriber.onError(httpServer.cause());
                    }
                })).toBlocking().value();
    }

    private static class SettableStatusRequestHandler implements Handler<HttpServerRequest> {

        private final AtomicInteger status;

        private SettableStatusRequestHandler(int status) {
            this.status = new AtomicInteger(status);
        }


        @Override
        public void handle(HttpServerRequest request) {
            request.response().setStatusCode(status.get()).end();
        }
    }

    private ServiceList getServices() {
        return Single.<ServiceList>create(subscriber ->
                httpClient.getNow(8080, "0.0.0.0", "/service",
                        response -> response.bodyHandler(buffer -> subscriber.onSuccess(Json.decodeValue(buffer, ServiceList.class))))).toBlocking().value();
    }


    private HttpClientResponse addService(NewService service) {
        return Single.<HttpClientResponse>create(subscriber -> {
            httpClient.post(8080, "0.0.0.0", "/service").handler(subscriber::onSuccess).end(Json.encode(service));
        }).toBlocking().value();
    }

    private HttpClientResponse delete(String id) {
        return Single.<HttpClientResponse>create(subscriber -> httpClient.delete(8080, "0.0.0.0", "/service/" + id).handler(subscriber::onSuccess).end()).toBlocking().value();
    }

    private interface Condition {
        void check();
    }

    private void await(Condition condition) throws InterruptedException {
        AssertionError exception = null;
        for (int i = 0; i < 1000; i++) {
            try {
                condition.check();
                return;
            } catch (AssertionError e) {
                exception = e;
            }
            Thread.sleep(10);
        }
        if (exception != null) {
            throw exception;
        }
    }
}
