package br.com.fiap.soat.mecanica.os.adapters.in.web.security;

import br.com.fiap.soat.mecanica.os.application.security.TipoPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioAutenticadoAdapterTest {

    private final UsuarioAutenticadoAdapter adapter = new UsuarioAutenticadoAdapter();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UsuarioAutenticado usuario) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
    }

    @Test
    void funcionarioTemSubTipoECargo() {
        UUID sub = UUID.randomUUID();
        autenticar(new UsuarioAutenticado(sub, UsuarioAutenticado.TipoPrincipal.FUNCIONARIO, "ATENDENTE"));

        assertThat(adapter.sub()).isEqualTo(sub);
        assertThat(adapter.tipo()).isEqualTo(TipoPrincipal.FUNCIONARIO);
        assertThat(adapter.cargo()).contains("ATENDENTE");
    }

    @Test
    void clienteNaoTemCargo() {
        autenticar(new UsuarioAutenticado(UUID.randomUUID(), UsuarioAutenticado.TipoPrincipal.CLIENTE, null));

        assertThat(adapter.tipo()).isEqualTo(TipoPrincipal.CLIENTE);
        assertThat(adapter.cargo()).isEmpty();
    }

    @Test
    void semUsuarioAutenticadoFalha() {
        assertThatThrownBy(adapter::sub).isInstanceOf(IllegalStateException.class)
                .hasMessage("usuario_nao_autenticado");
    }
}
