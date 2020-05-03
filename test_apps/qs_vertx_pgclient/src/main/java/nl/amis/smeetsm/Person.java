package nl.amis.smeetsm;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;


public class Person {

    private long id;
    private String firstName;
    private String lastName;

    public Person() {
    }

    public Person(Long id, String firstName, String lastName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public static Uni<Person> findById(PgPool client, Long id) {
        return client.preparedQuery("SELECT id, first_name, last_name  FROM person WHERE id = $1", Tuple.of(id))
                .onItem()
                .produceUni( rows -> {
                    if (rows.iterator().hasNext()) {
                        return Uni.createFrom().item(Person.from(rows.iterator().next()));
                    } else {
                        return Uni.createFrom().failure(new Throwable("Person not found with id: ".concat(id.toString())));
                    }
                });
    }

    private static Person from(Row row) {
        return new Person(row.getLong("id"), row.getString("first_name"), row.getString("last_name"));
    }

    public static Multi<Person> findAll(PgPool client) {
        return client.query("SELECT id, first_name, last_name FROM person")
                .onItem()
                .produceMulti(rows -> Multi.createFrom().iterable(rows))
                .map(Person::from);
    }


    public static Uni<Boolean> delete(PgPool client, Long id) {
        return client.begin()
                .flatMap(tx -> tx
                        .preparedQuery("DELETE FROM person WHERE id = $1", Tuple.of(id))
                        .onItem().produceUni(results -> {
                            tx.commitAndForget();
                            return Uni.createFrom().item(true);
                        })
                        .onFailure().recoverWithUni(t -> {
                            tx.commitAndForget();
                            return Uni.createFrom().item(false);
                        })
                );
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

    public Uni<Long> save(PgPool client) {
        return client.begin().onItem()
                .produceUni(tx -> tx
                        .preparedQuery("INSERT INTO person (first_name,last_name) VALUES ($1,$2) RETURNING (id)", Tuple.of(firstName, lastName))
                        .onItem().produceUni(results -> {
                            tx.commitAndForget();
                            return Uni.createFrom().item(results.iterator().next().getLong("id"));
                        })
                        .onFailure().invoke(t -> tx.rollbackAndForget())
                );
    }

}
