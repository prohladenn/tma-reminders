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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

        URI databaseUri = parseDatabaseUri(rawUrl);
        validateDatabaseUri(databaseUri, rawUrl);
        String jdbcUrl = toJdbcUrl(databaseUri);
        String username = Optional.ofNullable(environment.getProperty("DB_USER"))
                .or(() -> extractUserInfoPart(databaseUri, 0))
                .orElse(properties.getUsername());
        String password = Optional.ofNullable(environment.getProperty("DB_PASSWORD"))
                .or(() -> extractUserInfoPart(databaseUri, 1))
                .orElse(properties.getPassword());

        log.info("Configuring PostgreSQL datasource from DATABASE_URL");
        return properties.initializeDataSourceBuilder()
                .driverClassName("org.postgresql.Driver")
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    private URI parseDatabaseUri(String rawUrl) {
        String withoutJdbcPrefix = rawUrl.replaceFirst("^jdbc:", "");
        String normalized = withoutJdbcPrefix.replaceFirst("^postgres://", "postgresql://");
        return URI.create(normalized);
    }

    private String toJdbcUrl(URI uri) {
        String hostPort = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        String query = Optional.ofNullable(uri.getQuery())
                .filter(q -> !q.isBlank())
                .map(q -> "?" + q)
                .orElse("");

        return "jdbc:postgresql://" + hostPort + path + query;
    }

    private Optional<String> extractUserInfoPart(URI uri, int index) {
        return Optional.ofNullable(uri.getUserInfo())
                .filter(info -> !info.isBlank())
                .map(info -> info.split(":", 2))
                .filter(parts -> index < parts.length)
                .map(parts -> URLDecoder.decode(parts[index], StandardCharsets.UTF_8));
    }

    private void validateDatabaseUri(URI uri, String rawUrl) {
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL must include a hostname. Provided value: " + rawUrl);
        }
    }
}
