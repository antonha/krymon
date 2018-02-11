import com.fasterxml.jackson.datatype.joda.JodaModule;
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
import org.junit.AfterClass;
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
    private static Krymon krymon;
    private static Vertx vertx;
    private static HttpClient httpClient;

    @BeforeClass
    public static void setupClass() throws IOException {
        vertx = Vertx.vertx();
        Json.mapper.registerModule(new JodaModule());
        krymon = new Krymon(vertx, tmpFile().getAbsolutePath(), CHECK_PERIOD);
        krymon.start();

        httpClient = vertx.createHttpClient();
    }

    private static File tmpFile() throws IOException {
        File file = File.createTempFile("krymon-test", ".json");
        file.delete();
        file.deleteOnExit();
        return file;
    }

    @AfterClass
    public static void afterClass() {
        vertx.close();
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

        delete(service.getId());
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void shouldCheckStatusOfDifferentServices() throws InterruptedException {
        HttpServer server1 = startServerWithStatus(200);
        addService(new NewService("service1", "http://0.0.0.0:" + server1.actualPort()));

        HttpServer server2 = startServerWithStatus(500);
        addService(new NewService("service2", "http://0.0.0.0:" + server2.actualPort()));

        Thread.sleep(5 * CHECK_PERIOD);

        List<Service> services = getServices().getServices();
        Service service1 = withName(services, "service1");
        assertEquals(Service.Status.OK, service1.getStatus());
        Service service2 = withName(services, "service2");
        assertEquals(Service.Status.FAIL, service2.getStatus());

        delete(service1.getId());
        delete(service2.getId());
        assertTrue(getServices().getServices().isEmpty());
    }

    private Service withName(List<Service> services, String name) {
        for(Service s: services){
            if(name.equals(s.getName())){
                return s;
            }
        }
        return null;
    }

    @Test
    public void shouldHandleChangingStatus() throws InterruptedException {
        HttpServer server = startServerWithStatus(200);
        addService(new NewService("server", "http://0.0.0.0:" + server.actualPort()));

        Thread.sleep(10 * CHECK_PERIOD);
        Service service = getServices().getServices().get(0);
        assertEquals(Service.Status.OK, service.getStatus());
        assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 2);

        setStatus(server, 500);
        Thread.sleep(10 * CHECK_PERIOD);
        Service failingService = getServices().getServices().get(0);
        assertEquals(Service.Status.FAIL, failingService.getStatus());
        assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 10);

        setStatus(server, 200);
        Thread.sleep(10 * CHECK_PERIOD);
        Service onceAgainSucceding = getServices().getServices().get(0);
        assertEquals(Service.Status.OK, onceAgainSucceding.getStatus());
        assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 10);

        delete(onceAgainSucceding.getId());
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void shouldHandleFoobarPort() throws InterruptedException {
        addService(new NewService("server", "http://0.0.0.0:" + 124555));
        Thread.sleep(10 * CHECK_PERIOD);
        Service service = getServices().getServices().get(0);
        assertEquals(Service.Status.FAIL, service.getStatus());
        assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 10);
        delete(service.getId());
        assertTrue(getServices().getServices().isEmpty());
    }

    @Test
    public void shouldHandleFoobarUrl() throws InterruptedException {
        addService(new NewService("server", "8654###{]+[{}("));
        Thread.sleep(10 * CHECK_PERIOD);
        Service service = getServices().getServices().get(0);
        assertEquals(Service.Status.FAIL, service.getStatus());
        assertTrue(Seconds.secondsBetween(service.getLastCheck(), DateTime.now()).getSeconds() < 10);
        delete(service.getId());
        assertTrue(getServices().getServices().isEmpty());
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
}
