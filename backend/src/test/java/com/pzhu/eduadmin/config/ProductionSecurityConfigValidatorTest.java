package com.pzhu.eduadmin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Production security configuration contract")
class ProductionSecurityConfigValidatorTest {

    private static final String VALID_JWT_SECRET = "a-production-jwt-secret-with-32-plus-bytes";
    private static final String VALID_DATABASE_PASSWORD = "a-strong-database-password";
    private static final String VALID_ALLOWED_ORIGIN = "http://8.145.58.241";

    @Test
    @DisplayName("valid production configuration is accepted")
    void validProductionConfigurationIsAccepted() {
        ProductionSecurityConfigValidator validator = validator(
                VALID_JWT_SECRET,
                VALID_DATABASE_PASSWORD,
                VALID_ALLOWED_ORIGIN
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "blank JWT secret [{index}] is rejected")
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void blankJwtSecretIsRejected(String jwtSecret) {
        ProductionSecurityConfigValidator validator = validator(
                jwtSecret,
                VALID_DATABASE_PASSWORD,
                VALID_ALLOWED_ORIGIN
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @ParameterizedTest(name = "weak JWT secret [{index}] is rejected")
    @ValueSource(strings = {
            "short-secret",
            "dev-local-secret-key-for-edu-admin-2026",
            "demo-secret-key-change-in-prod"
    })
    void weakOrDevelopmentJwtSecretIsRejected(String jwtSecret) {
        ProductionSecurityConfigValidator validator = validator(
                jwtSecret,
                VALID_DATABASE_PASSWORD,
                VALID_ALLOWED_ORIGIN
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @ParameterizedTest(name = "unsafe database password [{index}] is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "123456", "password", "short", "twelve-bytes", "edu_admin"})
    void unsafeDatabasePasswordIsRejected(String databasePassword) {
        ProductionSecurityConfigValidator validator = validator(
                VALID_JWT_SECRET,
                databasePassword,
                VALID_ALLOWED_ORIGIN
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database password");
    }

    @ParameterizedTest(name = "blank CORS origins [{index}] are rejected")
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void blankCorsOriginsAreRejected(String allowedOrigins) {
        ProductionSecurityConfigValidator validator = validator(
                VALID_JWT_SECRET,
                VALID_DATABASE_PASSWORD,
                allowedOrigins
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS origins");
    }

    @ParameterizedTest(name = "wildcard CORS origin [{index}] is rejected")
    @ValueSource(strings = {"*", "http://example.com:*", "http://*.example.com"})
    void wildcardCorsOriginIsRejected(String allowedOrigins) {
        ProductionSecurityConfigValidator validator = validator(
                VALID_JWT_SECRET,
                VALID_DATABASE_PASSWORD,
                allowedOrigins
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcards");
    }

    @Test
    @DisplayName("empty CORS list elements are rejected")
    void blankCorsListElementIsRejected() {
        ProductionSecurityConfigValidator validator = validator(
                VALID_JWT_SECRET,
                VALID_DATABASE_PASSWORD,
                VALID_ALLOWED_ORIGIN + ","
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS origins");
    }

    @ParameterizedTest(name = "development CORS origin [{index}] is rejected")
    @ValueSource(strings = {"http://localhost:5173", "http://127.0.0.1:5173"})
    void developmentCorsOriginIsRejected(String allowedOrigins) {
        ProductionSecurityConfigValidator validator = validator(
                VALID_JWT_SECRET,
                VALID_DATABASE_PASSWORD,
                allowedOrigins
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development hosts");
    }

    @Test
    @DisplayName("validation runs during startup for the prod profile")
    void validationRunsDuringProdStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionSecurityConfigValidator.class)
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    @DisplayName("validator is not active outside the prod profile")
    void validatorIsNotActiveOutsideProd() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionSecurityConfigValidator.class)
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProductionSecurityConfigValidator.class);
                });
    }

    private ProductionSecurityConfigValidator validator(
            String jwtSecret,
            String databasePassword,
            String allowedOrigins
    ) {
        return new ProductionSecurityConfigValidator(jwtSecret, databasePassword, allowedOrigins);
    }
}
