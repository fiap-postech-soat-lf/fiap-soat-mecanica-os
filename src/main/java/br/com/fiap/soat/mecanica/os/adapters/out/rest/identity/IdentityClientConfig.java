package br.com.fiap.soat.mecanica.os.adapters.out.rest.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient próprio do Identity. Parte do builder do Spring (timeouts de 2 s e X-Correlation-Id do
 * {@code RestClientConfig}) e acrescenta o repasse do JWT, que não vale para outros destinos (F4-b).
 */
@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityClientConfig {

    @Bean
    RestClient identityRestClient(RestClient.Builder builder, IdentityProperties propriedades) {
        return builder
                .baseUrl(propriedades.baseUrl() == null ? "" : propriedades.baseUrl())
                .requestInterceptor(new RepasseAutorizacaoInterceptor())
                .build();
    }
}
