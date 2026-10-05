package ch.brogli.backendpagination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/** Runs the app against a throwaway Postgres container via {@code :backend:bootTestRun}. */
@TestConfiguration(proxyBeanMethods = false)
class TestBookPaginationApplication {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16");
    }

    static void main(String[] args) {
        SpringApplication.from(BookPaginationApplication::main)
                .with(TestBookPaginationApplication.class)
                .run(args);
    }
}
