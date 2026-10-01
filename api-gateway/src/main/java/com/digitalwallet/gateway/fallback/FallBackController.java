package com.digitalwallet.gateway.fallback;

import org.springframework.web.bind.annotation.RestController;

import io.micrometer.tracing.Tracer;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class FallBackController {

    private final Tracer tracer;

    public FallBackController(Tracer tracer) {
        this.tracer = tracer;
    }

    @RequestMapping("/fallback/account-service")
    public Mono<ResponseEntity<Map<String, Object>>> accountServiceFallback() {
        String traceId = tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId()
                : "unknown";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("message", "Serviço account-service indisponível no momento");
        body.put("traceId", traceId);

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }

}
