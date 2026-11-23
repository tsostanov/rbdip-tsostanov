package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NormalizationMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private PGSimpleDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromOriginalSchema() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
    }

    @Test
    void preservesOrdersAndDeduplicatesOnlyIdenticalContacts() {
        jdbc.update("""
                INSERT INTO orders (customer_full_name, customer_address, customer_phone, status, created_at)
                VALUES ('Anna', 'Moscow', NULL, 'paid', '2026-01-02 03:04:05'),
                       ('Anna', 'Moscow', NULL, 'new', '2026-01-03 03:04:05'),
                       ('Anna', 'SPb', NULL, 'new', '2026-01-04 03:04:05'),
                       ('Anna', 'Moscow', '', 'new', '2026-01-05 03:04:05'),
                       ('Ivan', NULL, NULL, 'new', '2026-01-06 03:04:05'),
                       ('Ivan', NULL, NULL, 'new', '2026-01-07 03:04:05')
                """);
        var originalOrders = jdbc.queryForList("""
                SELECT id, customer_full_name AS full_name, customer_address AS address,
                       customer_phone AS phone, status, created_at FROM orders ORDER BY id
                """);

        migrate();

        assertThat(jdbc.queryForList("""
                SELECT o.id, c.full_name, c.address, c.phone, o.status, o.created_at
                FROM orders o JOIN customers c ON c.id = o.customer_id ORDER BY o.id
                """))
                .isEqualTo(originalOrders);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT customer_id) FROM orders WHERE id IN (1, 2)
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT customer_id) FROM orders WHERE id IN (1, 3, 4)
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM orders o JOIN customers c ON c.id = o.customer_id
                WHERE c.full_name = 'Ivan' AND c.address IS NULL AND c.phone IS NULL
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void preservesItemsWithMissingChangedOrDuplicateCatalogProducts() {
        jdbc.update("INSERT INTO orders (customer_full_name) VALUES ('Anna')");
        jdbc.update("""
                INSERT INTO products (name, price, description)
                VALUES ('Book', 100.00, 'original'), ('Book', 100.00, 'duplicate'), ('Book', 120.00, 'new price')
                """);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_name, product_price, quantity)
                VALUES (1, 'Book', 100.00, 2), (1, 'Book', 90.00, 1),
                       (1, 'Deleted', 50.00, 3), (1, 'Deleted', 50.00, 4)
                """);
        var originalItems = jdbc.queryForList("""
                SELECT id, order_id, product_name AS name, product_price AS price, quantity
                FROM order_items ORDER BY id
                """);

        migrate();

        assertThat(jdbc.queryForList("""
                SELECT i.id, i.order_id, p.name, p.price, i.quantity
                FROM order_items i JOIN products p ON p.id = i.product_id ORDER BY i.id
                """)).isEqualTo(originalItems);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT product_id FROM order_items WHERE id = 1", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT description FROM products WHERE id = 1", String.class))
                .isEqualTo("original");
        assertThat(migrate().migrationsExecuted).isZero();
    }

    @Test
    void emptyDatabaseMigratesAndRejectsBrokenReferences() {
        migrate();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO orders (customer_id) VALUES (999)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO orders (customer_id) VALUES (NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO customers (full_name) VALUES ('Anna')");
        Long orderId = jdbc.queryForObject(
                "INSERT INTO orders (customer_id) VALUES (1) RETURNING id", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, 999, 1)
                """, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, NULL, 1)
                """, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private org.flywaydb.core.api.output.MigrateResult migrate() {
        return Flyway.configure().dataSource(dataSource).load().migrate();
    }
}
