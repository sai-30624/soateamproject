package com.payvault.auth.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.springframework.http.HttpStatus;

/** Turns an error response from another service into an ApiException with the same status and message. */
public final class FeignErrors {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeignErrors() {
    }

    public static boolean isClientError(FeignException fe) {
        return fe.status() >= 400 && fe.status() < 500;
    }

    public static ApiException toApi(FeignException fe) {
        HttpStatus status = HttpStatus.resolve(fe.status());
        if (status == null) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        }
        String message = status == HttpStatus.SERVICE_UNAVAILABLE ? "A dependent service is unavailable" : fe.getMessage();
        try {
            String content = fe.contentUTF8();
            if (content != null && !content.isBlank()) {
                JsonNode node = MAPPER.readTree(content);
                if (node.hasNonNull("error")) {
                    message = node.get("error").asText();
                }
            }
        } catch (Exception ignored) {
            // keep default message
        }
        return new ApiException(status, message);
    }
}
