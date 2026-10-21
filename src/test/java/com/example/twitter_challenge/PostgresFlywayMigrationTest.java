package com.example.twitter_challenge;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in integration test against a real, disposable PostgreSQL database.
 * Set POSTGRES_TEST_URL to a URL whose database name ends with "_test".
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresFlywayMigrationTest {

    @Test
    void cleanDatabaseMigratesThroughV6AndCreatesRequiredObjects() throws Exception {
        String url = System.getenv("POSTGRES_TEST_URL");
        assumeTrue(url != null && url.contains("_test"),
                "POSTGRES_TEST_URL must point to a disposable *_test database");
        String username = System.getenv().getOrDefault("POSTGRES_TEST_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "");

        resetPublicSchema(url, username, password);
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas("public")
                    .defaultSchema("public")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load();

            flyway.migrate();

            MigrationInfo current = flyway.info().current();
            assertEquals("6", current.getVersion().getVersion());
            assertEquals(
                    List.of("0", "1", "2", "3", "4", "5", "6"),
                    appliedVersions(url, username, password)
            );

            try (Connection connection = DriverManager.getConnection(url, username, password);
                 Statement statement = connection.createStatement()) {
                assertTrue(objectExists(statement, "public.users"));
                assertTrue(objectExists(statement, "public.tweets"));
                assertTrue(objectExists(statement, "public.notifications"));
                assertTrue(objectExists(statement, "public.subscriptions"));
                assertTrue(objectExists(statement, "public.stripe_webhook_events"));

                try (ResultSet resultSet = statement.executeQuery("""
                        SELECT 1 FROM pg_indexes
                        WHERE schemaname = 'public' AND indexname = 'idx_tweets_created_id'
                        """)) {
                    assertTrue(resultSet.next(), "idx_tweets_created_id is missing");
                }

                try (ResultSet resultSet = statement.executeQuery("""
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'subscriptions'
                          AND column_name = 'last_stripe_event_created_at'
                        """)) {
                    assertTrue(resultSet.next(), "subscriptions.last_stripe_event_created_at is missing");
                }

                try (ResultSet resultSet = statement.executeQuery("""
                        SELECT pg_get_constraintdef(oid) FROM pg_constraint
                        WHERE conname = 'notifications_type_check'
                        """)) {
                    assertTrue(resultSet.next(), "notifications type constraint is missing");
                    assertTrue(resultSet.getString(1).contains("MENTION"));
                }
            }
        } finally {
            resetPublicSchema(url, username, password);
        }
    }

    private List<String> appliedVersions(String url, String username, String password) throws Exception {
        List<String> versions = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT version FROM flyway_schema_history
                     WHERE version IS NOT NULL ORDER BY installed_rank
                     """)) {
            while (resultSet.next()) {
                versions.add(resultSet.getString(1));
            }
        }
        return versions;
    }

    private boolean objectExists(Statement statement, String name) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT to_regclass('" + name + "')")) {
            return resultSet.next() && resultSet.getString(1) != null;
        }
    }

    private void resetPublicSchema(String url, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }
}
