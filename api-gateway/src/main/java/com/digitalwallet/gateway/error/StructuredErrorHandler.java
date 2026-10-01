package com.digitalwallet.gateway.error;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import tools.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class StructuredErrorHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public StructuredErrorHandler(ObjectMapper objectMapper, Tracer tracer) {
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = (ex instanceof ResponseStatusException responseStatusException)
                ? HttpStatus.valueOf(responseStatusException.getStatusCode().value())
                : HttpStatus.INTERNAL_SERVER_ERROR;

        String message = status == HttpStatus.NOT_FOUND
                ? "Rota não encontrada"
                : status.getReasonPhrase();

        String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : "unknown";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("message", message);
        body.put("traceId", traceId);

        byte[] bytes;

        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{}".getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));

    }

}
