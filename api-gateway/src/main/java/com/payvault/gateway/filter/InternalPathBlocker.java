package com.payvault.gateway.filter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Blocks service-to-service endpoints at the edge.
 *
 * A Gateway GlobalFilter only runs for requests that matched a route, and no route matches /internal/**,
 * so the check must live in a WebFilter, which runs for every request. Paths containing ".." segments
 * (raw or URL-encoded) are refused too, so /api/wallets/../../internal/... can never be forwarded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPathBlocker implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String raw = exchange.getRequest().getURI().getRawPath();
        String path;
        try {
            path = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException malformedEscape) {
            path = "..";   // cannot be decoded safely -> refuse it
        }
        if (path.startsWith("/internal") || path.contains("/internal/") || path.contains("..")) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] json = "{\"status\":403,\"error\":\"Internal endpoint\"}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(json);
            return response.writeWith(Mono.just(buffer));
        }
        return chain.filter(exchange);
    }
}
