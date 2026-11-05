package br.com.fiap.soat.mecanica.os.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.saga.timeout.*}: prazo de cada comando, margem sobre o {@code expiraEm} do orçamento (F4-i)
 * e, para o scheduler de timeouts, o intervalo entre varreduras e o número de tentativas.
 */
@ConfigurationProperties("app.saga.timeout")
public record SagaTimeoutProperties(Duration comando, int tentativas, Duration margemAprovacao, Duration intervalo) {

    public SagaTimeoutProperties {
        comando = comando == null ? Duration.ofMinutes(2) : comando;
        tentativas = tentativas <= 0 ? 3 : tentativas;
        margemAprovacao = margemAprovacao == null ? Duration.ofMinutes(1) : margemAprovacao;
        intervalo = intervalo == null ? Duration.ofSeconds(30) : intervalo;
    }
}
