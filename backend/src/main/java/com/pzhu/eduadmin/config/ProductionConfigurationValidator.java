package com.pzhu.eduadmin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Rejects unsafe runtime configuration before a production instance starts.
 */
@Component
@Profile("prod")
public class ProductionConfigurationValidator {

    private static final int MINIMUM_JWT_SECRET_BYTES = 32;
    private static final int MINIMUM_DATABASE_PASSWORD_CHARACTERS = 16;
    private static final Set<String> DEVELOPMENT_DATABASE_PASSWORDS = Set.of(
            "123456", "password", "root", "edu_admin");
    private static final Set<String> DEVELOPMENT_JWT_SECRETS = Set.of(
            "dev-local-secret-key-for-edu-admin-2026",
            "demo-secret-key-change-in-prod");

    public ProductionConfigurationValidator(
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        validateDatabasePassword(databasePassword);
        validateJwtSecret(jwtSecret);
        validateCorsOrigins(allowedOrigins);
    }

    private static void validateDatabasePassword(String databasePassword) {
        if (isBlank(databasePassword)
                || databasePassword.length() < MINIMUM_DATABASE_PASSWORD_CHARACTERS
                || DEVELOPMENT_DATABASE_PASSWORDS.contains(databasePassword)) {
            throw new IllegalStateException("Production database password must be explicitly configured and non-default");
        }
    }

    private static void validateJwtSecret(String jwtSecret) {
        if (isBlank(jwtSecret)
                || DEVELOPMENT_JWT_SECRETS.contains(jwtSecret)
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_JWT_SECRET_BYTES) {
            throw new IllegalStateException("Production JWT secret must be non-default and at least 32 UTF-8 bytes");
        }
    }

    private static void validateCorsOrigins(String allowedOrigins) {
        if (isBlank(allowedOrigins)) {
            throw new IllegalStateException("Production CORS origins must be explicitly configured");
        }

        for (String origin : allowedOrigins.split(",", -1)) {
            String value = origin.trim();
            if (value.contains("*") || !isExactHttpOrigin(value)) {
                throw new IllegalStateException("Production CORS origin must be an exact HTTP(S) origin");
            }
        }
    }

    private static boolean isExactHttpOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (path == null || path.isEmpty() || "/".equals(path));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
