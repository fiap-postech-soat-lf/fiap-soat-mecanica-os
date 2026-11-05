package br.com.fiap.soat.mecanica.os.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Relógio injetável, para o domínio e a Saga não chamarem {@code Instant.now()} direto. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
