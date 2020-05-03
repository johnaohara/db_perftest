package nl.amis.smeetsm;

import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RoutingExchange;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgPool;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.util.Optional;

@Singleton
public class PersonRoute {

    @Inject
    PgPool client;

    @Route(path = "/people", methods = HttpMethod.GET)
    void getPeople(final RoutingExchange ex) {
        JsonArray resultArr = new JsonArray();

        Person.findAll(client)
                .subscribe()
                .with(person -> resultArr.add(JsonObject.mapFrom(person)),
                        () -> {
                            ex.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").setStatusCode(200).end(resultArr.toString());
                        }
                );
    }


    @Route(path = "/people/:id", methods = HttpMethod.GET)
    void getSingle(final RoutingExchange ex) {
        Optional<String> id = ex.getParam("id");
        if (id.isPresent()) {
            try {
                Person.findById(client, Long.valueOf(id.get()))
                        .subscribe()
                        .with(person -> ex.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").setStatusCode(200).end(JsonObject.mapFrom(person).toString()),
                                failure -> ex.response().setStatusCode(404).end(failure.getMessage())
                        );
            } catch (NumberFormatException nfe){
                ex.response().setStatusCode(400).end();
            }
        } else {
            ex.response().setStatusCode(400).end();
        }
    }

    @Route(path = "/people", methods = HttpMethod.POST)
    void create(final RoutingExchange ex) {
        ex.context().getBodyAsJson()
                .mapTo(Person.class)
                .save(client)
                .subscribe()
                .with(id -> ex.response().putHeader("Location", URI.create("/people/" + id).toString()).setStatusCode(201).end(),
                        failure -> ex.response().setStatusCode(500).end(failure.getMessage())
                );
    }

    @Route(path = "/people", methods = HttpMethod.DELETE)
    void delete(final RoutingExchange ex) {
        Optional<String> id = ex.getParam("id");
        if (id.isPresent()) {
            Person.delete(client, Long.valueOf(id.get()))
                    .subscribe()
                    .with(success -> ex.response().setStatusCode(success ? 204 : 404).end(),
                            failure -> ex.response().setStatusCode(500).end(failure.getMessage()));
        } else {
            ex.response().setStatusCode(400).end();
        }
    }
}
