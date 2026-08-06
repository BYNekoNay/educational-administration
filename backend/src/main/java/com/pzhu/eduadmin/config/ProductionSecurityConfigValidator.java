package com.pzhu.eduadmin.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
@Profile("prod")
public class ProductionSecurityConfigValidator {

    private static final int MIN_SECRET_BYTES = 32;
    private static final int MIN_DATABASE_PASSWORD_BYTES = 16;
    private static final Set<String> DEVELOPMENT_JWT_SECRETS = Set.of(
            "dev-local-secret-key-for-edu-admin-2026",
            "demo-secret-key-change-in-prod"
    );
    private static final Set<String> DEVELOPMENT_DATABASE_PASSWORDS = Set.of(
            "123456",
            "admin",
            "changeme",
            "change-me",
            "password",
            "root",
            "edu_admin"
    );

    private final String jwtSecret;
    private final String databasePassword;
    private final String allowedOrigins;

    public ProductionSecurityConfigValidator(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        this.jwtSecret = jwtSecret;
        this.databasePassword = databasePassword;
        this.allowedOrigins = allowedOrigins;
    }

    @PostConstruct
    public void validate() {
        validateJwtSecret();
        validateDatabasePassword();
        validateAllowedOrigins();
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw invalid("JWT secret must be configured");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw invalid("JWT secret must be at least 32 UTF-8 bytes");
        }
        if (DEVELOPMENT_JWT_SECRETS.contains(jwtSecret)) {
            throw invalid("JWT secret must not use the development default");
        }
    }

    private void validateDatabasePassword() {
        if (databasePassword == null || databasePassword.isBlank()) {
            throw invalid("database password must be configured");
        }

        String normalizedPassword = databasePassword.trim().toLowerCase(Locale.ROOT);
        if (databasePassword.getBytes(StandardCharsets.UTF_8).length < MIN_DATABASE_PASSWORD_BYTES
                || DEVELOPMENT_DATABASE_PASSWORDS.contains(normalizedPassword)) {
            throw invalid("database password is weak or uses a development default");
        }
    }

    private void validateAllowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw invalid("CORS origins must be configured");
        }

        for (String configuredOrigin : allowedOrigins.split(",", -1)) {
            String origin = configuredOrigin.trim();
            if (origin.isEmpty()) {
                throw invalid("CORS origins must not contain blank entries");
            }
            if (origin.contains("*")) {
                throw invalid("CORS origins must not contain wildcards");
            }

            URI uri = parseOrigin(origin);
            String host = uri.getHost();
            if (host == null || !isHttpScheme(uri.getScheme()) || !isExactOrigin(uri)) {
                throw invalid("CORS origins must be exact HTTP or HTTPS origins");
            }
            if (isDevelopmentHost(host)) {
                throw invalid("CORS origins must not use development hosts");
            }
        }
    }

    private URI parseOrigin(String origin) {
        try {
            return new URI(origin);
        } catch (URISyntaxException exception) {
            throw invalid("CORS origins must be valid origins");
        }
    }

    private boolean isHttpScheme(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isExactOrigin(URI uri) {
        return uri.getRawUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    private boolean isDevelopmentHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }

    private IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid production security configuration: " + reason);
    }
}
