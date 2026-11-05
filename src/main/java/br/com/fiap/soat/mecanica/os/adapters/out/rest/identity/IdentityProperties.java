package br.com.fiap.soat.mecanica.os.adapters.out.rest.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.identity.*}: URL base do Identity (DNS do cluster) e a política de retry (F4-c). */
@ConfigurationProperties("app.identity")
public record IdentityProperties(String baseUrl, Retry retry) {

    public IdentityProperties {
        retry = retry == null ? new Retry(3, Duration.ofMillis(200), 2.0) : retry;
    }

    public record Retry(int maxTentativas, Duration esperaInicial, double multiplicador) {

        public Retry {
            maxTentativas = maxTentativas <= 0 ? 3 : maxTentativas;
            esperaInicial = esperaInicial == null ? Duration.ofMillis(200) : esperaInicial;
            multiplicador = multiplicador <= 0 ? 2.0 : multiplicador;
        }
    }
}
