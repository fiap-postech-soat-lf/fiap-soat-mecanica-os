package br.com.fiap.soat.mecanica.os.bdd;

import br.com.fiap.soat.mecanica.os.support.IntegracaoTest;
import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Liga o Cucumber ao contexto Spring dos ITs. Estende o {@link IntegracaoTest} para o Spring reaproveitar o contexto
 * do cache (um só consumidor SQS e uma só outbox no build inteiro) e herdar os containers e as propriedades.
 */
@CucumberContextConfiguration
public class CucumberSpringConfig extends IntegracaoTest {
}
