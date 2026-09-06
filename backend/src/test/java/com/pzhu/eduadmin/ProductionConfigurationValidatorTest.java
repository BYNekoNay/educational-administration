package com.pzhu.eduadmin;

import com.pzhu.eduadmin.config.ProductionConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {

    private static final String SAFE_SECRET = "production-secret-with-at-least-32-utf8-bytes";
    private static final String SAFE_ORIGIN = "https://admin.example.com,https://app.example.com";

    @Test
    void acceptsExplicitProductionConfiguration() {
        assertThatCode(() -> new ProductionConfigurationValidator(
                "database-password-not-a-development-default", SAFE_SECRET, SAFE_ORIGIN))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAndKnownDevelopmentSecrets() {
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "", SAFE_SECRET, SAFE_ORIGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database password");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "123456", SAFE_SECRET, SAFE_ORIGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database password");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "short-password", SAFE_SECRET, SAFE_ORIGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database password");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "database-password-not-a-development-default",
                "dev-local-secret-key-for-edu-admin-2026", SAFE_ORIGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "database-password-not-a-development-default", SAFE_SECRET, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS origins");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "database-password-not-a-development-default", SAFE_SECRET, SAFE_ORIGIN + ","))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS origin");
    }

    @Test
    void rejectsWeakJwtSecretAndWildcardCorsOrigin() {
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "database-password-not-a-development-default", "short", SAFE_ORIGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                "database-password-not-a-development-default", SAFE_SECRET, "https://*.example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS origin");
    }

    @Test
    void productionProfileRejectsUnsafeConfigurationDuringContextStartup() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            TestPropertyValues.of(
                    "spring.datasource.password=123456",
                    "jwt.secret=" + SAFE_SECRET,
                    "app.cors.allowed-origins=" + SAFE_ORIGIN).applyTo(context);
            context.register(ProductionConfigurationValidator.class);

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(BeanCreationException.class)
                    .hasRootCauseInstanceOf(IllegalStateException.class);
        }
    }
}
