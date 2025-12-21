package com.tma.reminders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.util.Optional;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties, Environment environment) {
        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            return properties.initializeDataSourceBuilder().build();
        }

        String jdbcUrl = toJdbcUrl(rawUrl);
        String username = environment.getProperty("DB_USER", properties.getUsername());
        String password = environment.getProperty("DB_PASSWORD", properties.getPassword());

        log.info("Configuring PostgreSQL datasource from DATABASE_URL");
        return properties.initializeDataSourceBuilder()
                .driverClassName("org.postgresql.Driver")
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    private String toJdbcUrl(String rawUrl) {
        String normalized = rawUrl.replaceFirst("^postgres://", "postgresql://");
        URI uri = URI.create(normalized);

        String hostPort = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        String query = Optional.ofNullable(uri.getQuery())
                .filter(q -> !q.isBlank())
                .map(q -> "?" + q)
                .orElse("");

        return "jdbc:postgresql://" + hostPort + uri.getPath() + query;
    }
}
