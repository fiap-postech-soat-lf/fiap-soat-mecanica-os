package br.com.fiap.soat.mecanica.os.adapters.out.persistence.servico;

import br.com.fiap.soat.mecanica.os.domain.servico.Servico;
import org.springframework.stereotype.Component;

@Component
public class ServicoMapper {

    public ServicoEntity toEntity(Servico servico) {
        ServicoEntity entity = new ServicoEntity();
        entity.setId(servico.getId());
        entity.setStatus(servico.getStatus());
        entity.setNome(servico.getNome());
        entity.setDescricao(servico.getDescricao());
        return entity;
    }

    public Servico toDomain(ServicoEntity entity) {
        return Servico.reconstruir(entity.getId(), entity.getStatus(), entity.getNome(), entity.getDescricao());
    }
}
