package nl.amis.smeetsm;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

@Singleton
public class DatabaseConfiguration {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfiguration.class.getCanonicalName());
    @Inject
    PgPool client;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("The application is starting...");
        if (schemaCreate) {
            initdb();
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        LOGGER.info("The application is stopping...");
    }

    private String readFromInputStream(InputStream inputStream)
            throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        return resultStringBuilder.toString();
    }

    private void initdb() {
        System.out.println("InitDB");
        ClassLoader classLoader = getClass().getClassLoader();
        String data = "";
        try {
            InputStream inputStream = classLoader.getResourceAsStream("schema.sql");

            data = readFromInputStream(inputStream);
        } catch (Exception e) {
            data = "";
        }
        if (data != null && !data.equals("")) {
                final Uni<RowSet<Row>> query = client.query(data);
                query.await().indefinitely();
            }
        }
}
