package br.com.fiap.soat.mecanica.os.application.saga;

import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.os.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.MudancaSituacao;
import br.com.fiap.soat.mecanica.os.domain.ordemservico.OrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Eventos que o OS publica em {@code os-eventos}. O cancelamento publica só o OrdemServicoCancelada (F4-e). */
@Component
@RequiredArgsConstructor
public class EventosOrdemServico {

    public static final String DESTINO = "os-eventos";

    private final MensagemFactory fabrica;

    public MensagemEnvelope situacaoAlterada(UUID sagaId, OrdemServico os, MudancaSituacao mudanca) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", os.getId());
        payload.put("clienteId", os.getClienteId());
        payload.put("placa", os.getPlaca());
        payload.put("de", mudanca.de() == null ? null : mudanca.de().name());
        payload.put("para", mudanca.para().name());
        if (mudanca.motivo() != null) {
            payload.put("motivo", mudanca.motivo());
        }
        payload.put("ocorridaEm", mudanca.em());
        return fabrica.criar("SituacaoOrdemServicoAlterada", 1, sagaId, os.getId(), payload);
    }

    public MensagemEnvelope ordemServicoCancelada(UUID sagaId, OrdemServico os, MudancaSituacao mudanca) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("osId", os.getId());
        payload.put("clienteId", os.getClienteId());
        payload.put("motivo", mudanca.motivo());
        payload.put("ocorridaEm", mudanca.em());
        return fabrica.criar("OrdemServicoCancelada", 1, sagaId, os.getId(), payload);
    }
}
