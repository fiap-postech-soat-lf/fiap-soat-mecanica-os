package br.com.fiap.soat.mecanica.os.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida um envelope publicado contra o envelope.schema.json e o schema do payload do proprio OS
 * (regra de congelamento da F1). Os schemas vem de docs/contratos, copiados para contratos/ no classpath de teste.
 */
public final class ValidadorContratos {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ValidadorContratos() {
    }

    /** Erros do envelope e do payload (lista vazia = valido). */
    public static List<Error> validar(String envelopeJson) {
        try {
            JsonNode envelope = MAPPER.readTree(envelopeJson);
            List<Error> erros = new ArrayList<>(schema("contratos/envelope.schema.json")
                    .validate(envelopeJson, InputFormat.JSON));
            String schemaPayload = "contratos/%s.v%d.schema.json"
                    .formatted(envelope.path("type").asText(), envelope.path("version").asInt());
            erros.addAll(schema(schemaPayload).validate(envelope.path("payload").toString(), InputFormat.JSON));
            return erros;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Schema schema(String caminho) throws IOException {
        String conteudo = new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        return REGISTRY.getSchema(conteudo, InputFormat.JSON);
    }
}
