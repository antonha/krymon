package krymon;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import rx.Single;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Krymon {

    private final static Logger log = LoggerFactory.getLogger(Krymon.class.getName());
    private final String storeFile;
    private final Vertx vertx;
    private boolean running = false;
    private HttpClient httpClient;

    public Krymon(Vertx vertx, String storeFile, long period) {
        this.vertx = vertx;
        this.storeFile = storeFile;
        vertx.setTimer(period, new Handler<Long>() {
            @Override
            public void handle(Long event) {
                if(running){
                    updateServices().subscribe(
                            ignore -> {
                                log.info("Successfully updated statuses of all services.");
                                vertx.setTimer(period, this);
                            },
                            error -> {
                                log.error("Failed to update list of services.", error);
                                vertx.setTimer(period, this);
                            }
                    );
                }
            }
        });
    }

    private Single<Void> updateServices() {
        return fileExists().flatMap(exists -> {
            if(exists){
                return readList().toObservable().flatMap(list -> rx.Observable.from(list.getServices()))
                        .flatMap(service -> updateStatus(service).toObservable())
                        .toList().toSingle()
                        .flatMap(newList -> writeList(new ServiceList(newList)));
            }
            else{
                return Single.just(null);
            }
        });
    }

    private Single<Service> updateStatus(Service oldService) {
        return get(oldService.getUrl()).map(this::statusFor)
                .onErrorReturn(t -> Service.Status.FAIL)
                .map(status -> new Service(
                        oldService.getId(),
                        oldService.getName(),
                        oldService.getUrl(),
                        status, DateTime.now()));
    }

    private Service.Status statusFor(HttpClientResponse resp) {
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return Service.Status.OK;
        } else {
            return Service.Status.FAIL;
        }
    }

    private Single<HttpClientResponse> get(String url) {
        return Single.create(subscriber -> httpClient.getAbs(url, subscriber::onSuccess)
                .setTimeout(5000)
                .exceptionHandler(subscriber::onError).end());
    }

    public synchronized Single<Void> start() {
        if (!running) {
            running = true;
            this.httpClient = vertx.createHttpClient();
            return listen(vertx.createHttpServer().requestHandler(createRouter(vertx)::accept), 8080)
                    .doOnEach(ig -> log.info("Krymon listening on port {0}", String.valueOf(8080)))
                    .doOnError(t -> log.error("Failed to start on port {0}", t, String.valueOf(8080)));
        } else {
            return Single.just(null);
        }

    }

    private Single<Void> listen(HttpServer httpServer, int port) {
        return Single.create(subscriber ->
                httpServer.listen(port, handler -> {
                    if (handler.succeeded()) {
                        subscriber.onSuccess(null);
                    } else {
                        subscriber.onError(handler.cause());
                    }
                }));
    }

    private Router createRouter(Vertx vertx) {
        Router router = Router.router(vertx);
        router.get("/service").handler(this::getServices);
        router.post("/service").handler(this::addService);
        router.delete("/service/:serviceID").handler(this::deleteService);
        return router;
    }

    private void getServices(RoutingContext routingContext) {
        readListOrEmpty().subscribe(
                list -> routingContext.response().end(Json.encode(list)),
                e -> routingContext.response().setStatusCode(500).end()
        );
    }

    private void addService(RoutingContext routingContext) {
        readBody(routingContext)
                .map(b -> withRandomId(Json.decodeValue(b, NewService.class)))
                .flatMap(this::addService)
                .subscribe(
                        id -> {
                            log.info("Successfully added service with id {0}", id);
                            routingContext.response().setStatusCode(201).putHeader("Location", "/service/" + id).end();
                        },
                        e -> {
                            log.error("Failed to add service.", e);
                            routingContext.response().setStatusCode(500).end();
                        }
                );
    }

    private Single<Buffer> readBody(RoutingContext routingContext) {
        return Single.create(subscriber -> routingContext.request().bodyHandler(subscriber::onSuccess));
    }

    private Single<String> addService(Service service) {
        return readListOrEmpty().flatMap(existingList -> {
            List<Service> services = new ArrayList<>(existingList.getServices());
            services.add(service);
            return writeList(new ServiceList(services));
        }).map(ig -> service.getId());
    }


    private Single<ServiceList> readListOrEmpty() {
        return fileExists().flatMap(exists -> {
            if (exists) {
                return readList();
            } else {
                return Single.just(new ServiceList(Collections.emptyList()));
            }
        });
    }

    private final Service withRandomId(NewService service) {
        return new Service(UUID.randomUUID().toString(), service.getName(), service.getUrl(), Service.Status.UNKNOWN, DateTime.now(DateTimeZone.UTC));
    }

    private void deleteService(RoutingContext routingContext) {
        String serviceID = routingContext.pathParam("serviceID");
        Single.just(serviceID)
                .flatMap(this::deleteIfExists)
                .subscribe(
                        existed -> {
                            if (existed) {
                                log.info("Successfully deleted service with id {0}", serviceID);
                                routingContext.response().setStatusCode(200).end();
                            } else {
                                log.info("Tried deleting non-existing service id {0}", serviceID);
                                routingContext.response().setStatusCode(404).end();
                            }
                        });
    }

    private Single<Boolean> deleteIfExists(String serviceID) {
        return fileExists().flatMap(fileExists -> {
            if (fileExists) {
                return readList().flatMap(services -> {
                    List<Service> list = services.getServices();
                    if (list.removeIf(s -> s.getId().equals(serviceID))) {
                        return writeList(new ServiceList(list)).map(ig -> Boolean.TRUE);
                    } else {
                        return Single.just(Boolean.FALSE);
                    }
                });
            } else {
                return Single.just(Boolean.FALSE);
            }
        });
    }

    private Single<Boolean> fileExists() {
        return Single.create(subscriber ->
                vertx.fileSystem().exists(storeFile, event -> {
                    if (event.succeeded()) {
                        subscriber.onSuccess(event.result());
                    } else {
                        subscriber.onError(event.cause());
                    }
                }));
    }

    private Single<ServiceList> readList() {
        return readFile().map(b -> Json.decodeValue(b, ServiceList.class));
    }

    private Single<Buffer> readFile() {
        return Single.create(subscriber ->
                vertx.fileSystem().readFile(storeFile, buffer -> {
                    if (buffer.succeeded()) {
                        subscriber.onSuccess(buffer.result());
                    } else {
                        subscriber.onError(buffer.cause());
                    }
                }));
    }

    private Single<Void> writeList(ServiceList serviceList) {
        return Single.create(subscriber ->
                vertx.fileSystem().writeFile(storeFile, Json.encodeToBuffer(serviceList), handler -> {
                    if (handler.succeeded()) {
                        subscriber.onSuccess(handler.result());
                    } else {
                        subscriber.onError(handler.cause());
                    }
                }));
    }


}
