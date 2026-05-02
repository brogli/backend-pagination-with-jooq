package ch.brogli.backendpagination.indexaudit;

import org.testcontainers.containers.PostgreSQLContainer;

/** Postgres container shared across index-plan test classes via {@link #INSTANCE}. */
final class SeededPostgresContainer extends PostgreSQLContainer<SeededPostgresContainer> {

    static final SeededPostgresContainer INSTANCE = new SeededPostgresContainer();

    private SeededPostgresContainer() {
        super("postgres:16");
        withDatabaseName("books");
        withUsername("postgres");
        withPassword("postgres");
        withReuse(true);
    }
}
