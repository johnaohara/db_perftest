package nl.amis.smeetsm;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import sun.tools.jstat.Literal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;


public class Person {

    private long id;
    private String firstName;
    private String lastName;

    public Person(Long id, String firstName, String lastName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public static Uni<Person> findById(PgPool client, Long id) {
//        return client.preparedQuery("SELECT id, first_name, last_name  FROM person WHERE id = $1", Tuple.of(id))
//                .(PgRowSet -> {
//            return PgRowSet.iterator();
//        }).thenApply(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
        return null;

    }

    private static Person from(Row row) {
        return new Person(row.getLong("id"), row.getString("first_name"), row.getString("last_name"));
    }

    public static Multi<Person> findAll(PgPool client) {
        return client.query("SELECT id, first_name, last_name FROM person")
                .onItem()
                .produceMulti(rows -> Multi.createFrom().iterable(rows))
                .map(row -> from(row));
    }

    public static void findAllTest(PgPool client) {
/*
        client.preparedQuery("SELECT id, first_name, last_name FROM person")
                .subscribe().with(
                rows ->  System.out.println("Got Rows!"),
                failure -> failure.printStackTrace()
        );
*/




        //                .onItem()
//                .invoke(rows -> System.out.println("Got Rows!")
//                );
    }


    public static CompletionStage<Boolean> delete(PgPool client, Long id) {
//        return client.preparedQuery("DELETE FROM person WHERE id = $1", Tuple.of(id))
//                .thenApply(pgRowSet -> pgRowSet.rowCount() == 1);
        return null;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public CompletionStage<Long> save(PgPool client) {
//        return client.preparedQuery("INSERT INTO person (first_name,last_name) VALUES ($1,$2) RETURNING (id)", Tuple.of(firstName, lastName))
//                .thenApply(pgRowSet -> pgRowSet.iterator().next().getLong("id"));
        return null;

    }

}
