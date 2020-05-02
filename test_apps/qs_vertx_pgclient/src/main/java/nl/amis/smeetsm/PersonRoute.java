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

@Singleton
public class PersonRoute {

    @Inject
    PgPool client;

    @Route(path = "/people", methods = HttpMethod.GET)
    void getPeople(final RoutingExchange ex) {
        JsonArray resultArr = new JsonArray();

        Person.findAll(client)
                .subscribe()
                .with(
                        person -> resultArr.add(JsonObject.mapFrom(person)),
                        () -> {
                            ex.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").setStatusCode(200).end(resultArr.toString());
                        }
                );

    }


//    @Route(path = "/people", methods = HttpMethod.GET)
//    public CompletionStage<Response> getSingle(@PathParam("id") Long id) {
//        return Person.findById(client, id)
//                .thenApply(person -> person != null ? Response.ok(person) : Response.status(Response.Status.NOT_FOUND))
//                .thenApply(Response.ResponseBuilder::build);
//    }
//
//    @POST
//    public CompletionStage<Response> create(Person person) {
//        return person.save(client)
//                .thenApply(id -> URI.create("/people/" + id))
//                .thenApply(uri -> Response.created(uri).build());
//    }
//
//    @DELETE
//    @Path("{id}")
//    public CompletionStage<Response> delete(@PathParam("id") Long id) {
//        return Person.delete(client, id)
//                .thenApply(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
//                .thenApply(status -> Response.status(status).build());
//    }
}
