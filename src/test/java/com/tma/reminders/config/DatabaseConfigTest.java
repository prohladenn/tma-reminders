package com.tma.reminders.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConfigTest {

    private final DatabaseConfig config = new DatabaseConfig();

    @Test
    void shouldBuildDataSourceFromJdbcPrefixedUrl() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:h2:mem:testdb");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://db.example.com:5432/reminders?sslmode=require")
                .withProperty("DB_USER", "db_user")
                .withProperty("DB_PASSWORD", "s3cr3t");

        DataSource dataSource = config.dataSource(properties, environment);
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);

        HikariDataSource hikari = (HikariDataSource) dataSource;
        try {
            assertThat(hikari.getJdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:5432/reminders?sslmode=require");
            assertThat(hikari.getUsername()).isEqualTo("db_user");
            assertThat(hikari.getPassword()).isEqualTo("s3cr3t");
        } finally {
            hikari.close();
        }
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:h2:mem:testdb");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql:///reminders");

        assertThatThrownBy(() -> config.dataSource(properties, environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname");
    }
}
