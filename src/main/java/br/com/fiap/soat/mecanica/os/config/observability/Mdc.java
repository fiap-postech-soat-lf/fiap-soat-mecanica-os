package br.com.fiap.soat.mecanica.os.config.observability;

import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chaves de MDC usadas nos logs estruturados e helpers para abrir e fechar escopos.
 * O correlationId acompanha a requisicao HTTP e as mensagens; o requestId e mantido com o
 * mesmo valor para nao quebrar as queries de log da Fase 3.
 */
public final class Mdc {

    public static final String CORRELATION_ID = "correlationId";
    public static final String REQUEST_ID = "requestId";
    public static final String SAGA_ID = "sagaId";
    public static final String OS_ID = "osId";
    public static final String MESSAGE_ID = "messageId";

    private Mdc() {
    }

    public static String correlationId() {
        return MDC.get(CORRELATION_ID);
    }

    /**
     * Acrescenta {@code sagaId} e {@code osId} ao evento de log apenas quando o MDC ainda nao os tem.
     * O encoder estruturado escreve o MDC e os pares chave-valor no mesmo objeto JSON e descarta a
     * linha inteira se uma chave se repetir (consumidor SQS, que ja abre o MDC com os dois ids).
     */
    public static LoggingEventBuilder comSagaEOs(LoggingEventBuilder evento, UUID sagaId, UUID osId) {
        return comChaveAusente(comChaveAusente(evento, SAGA_ID, sagaId), OS_ID, osId);
    }

    /** Idem para eventos que so conhecem o {@code osId}. */
    public static LoggingEventBuilder comOs(LoggingEventBuilder evento, UUID osId) {
        return comChaveAusente(evento, OS_ID, osId);
    }

    private static LoggingEventBuilder comChaveAusente(LoggingEventBuilder evento, String chave, UUID valor) {
        return MDC.get(chave) == null ? evento.addKeyValue(chave, valor) : evento;
    }

    /**
     * Coloca os valores nao nulos no MDC e devolve um escopo que restaura os valores
     * anteriores no {@code close()}. Uso: {@code try (var ignored = Mdc.abrir(Map.of(...))) { ... }}.
     */
    public static Escopo abrir(Map<String, ?> valores) {
        Map<String, String> anteriores = new HashMap<>();
        Map<String, String> novos = new LinkedHashMap<>();
        valores.forEach((chave, valor) -> {
            if (valor != null) {
                anteriores.put(chave, MDC.get(chave));
                novos.put(chave, valor.toString());
            }
        });
        novos.forEach(MDC::put);
        return new Escopo(anteriores);
    }

    public static final class Escopo implements AutoCloseable {

        private final Map<String, String> anteriores;

        private Escopo(Map<String, String> anteriores) {
            this.anteriores = anteriores;
        }

        @Override
        public void close() {
            anteriores.forEach((chave, valor) -> {
                if (valor == null) {
                    MDC.remove(chave);
                } else {
                    MDC.put(chave, valor);
                }
            });
        }
    }
}
