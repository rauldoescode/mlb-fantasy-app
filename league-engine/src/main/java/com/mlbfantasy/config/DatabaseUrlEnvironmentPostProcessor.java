package com.mlbfantasy.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Lets the Java league-engine share the same {@code DATABASE_URL} env var as the
 * Python data worker instead of requiring a second, differently-formatted variable.
 *
 * <p>The data worker uses psycopg2's URL form
 * ({@code postgres(ql)://user:pass@host:port/db}), but Spring's JDBC datasource
 * needs {@code jdbc:postgresql://host:port/db} with credentials as separate
 * properties. This runs before the DataSource bean is created and normalizes one
 * into the other, so both services keep reading from a single {@code DATABASE_URL}.
 *
 * <p>If {@code DATABASE_URL} is already JDBC-formatted (starts with {@code jdbc:})
 * or isn't a recognizable Postgres URL, this is a no-op and the existing
 * {@code spring.datasource.url=${DATABASE_URL}} property in application.properties
 * is used as-is.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "databaseUrlNormalized";
    private static final int DEFAULT_POSTGRES_PORT = 5432;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank() || databaseUrl.startsWith("jdbc:")) {
            return;
        }

        try {
            URI uri = new URI(databaseUrl);
            String scheme = uri.getScheme();
            if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
                return; // Not a Postgres URL we know how to normalize; leave it alone
            }

            int port = uri.getPort() == -1 ? DEFAULT_POSTGRES_PORT : uri.getPort();
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("spring.datasource.url", jdbcUrl);

            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                int separator = userInfo.indexOf(':');
                if (separator >= 0) {
                    properties.put("spring.datasource.username", userInfo.substring(0, separator));
                    properties.put("spring.datasource.password", userInfo.substring(separator + 1));
                } else {
                    properties.put("spring.datasource.username", userInfo);
                }
            }

            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        } catch (URISyntaxException ex) {
            // Leave the environment untouched; the datasource will then fail fast
            // with a clear connection error instead of a confusing parsing one
        }
    }
}
