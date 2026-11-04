package com.payvault.txn.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.payvault.txn.dto.TxnDtos.MoneyRequest;
import com.payvault.txn.exception.ApiException;
import com.payvault.txn.exception.FeignErrors;
import com.payvault.txn.exception.WalletUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Component;

/**
 * All calls to Wallet Service go through here, protected by Resilience4j:
 *  - Retry: up to 3 attempts for network errors / 5xx (safe, because Wallet debit/credit is idempotent on txnRef).
 *  - Circuit breaker: after repeated failures, stop calling Wallet for 15 s and fail fast.
 * Business errors (4xx, e.g. "Insufficient balance") are NOT retried and do NOT trip the breaker.
 */
@Component
public class WalletGateway {

    private final WalletClient client;
    private final CircuitBreaker breaker;
    private final Retry retry;

    public WalletGateway(WalletClient client) {
        this.client = client;
        this.breaker = CircuitBreaker.of("wallet-service", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreExceptions(ApiException.class)
                .build());
        this.retry = Retry.of("wallet-service", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(400))
                .ignoreExceptions(ApiException.class, CallNotPermittedException.class)
                .build());
    }

    public void ensureWallet(Long userId) {
        call(() -> client.create(Map.of("userId", userId)));
    }

    public Map<String, Object> debit(Long userId, String txnRef, BigDecimal amount) {
        return call(() -> client.debit(userId, new MoneyRequest(txnRef, amount)));
    }

    public Map<String, Object> credit(Long userId, String txnRef, BigDecimal amount) {
        return call(() -> client.credit(userId, new MoneyRequest(txnRef, amount)));
    }

    public Map<String, Object> state() {
        CircuitBreaker.Metrics m = breaker.getMetrics();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("circuitBreaker", breaker.getName());
        s.put("state", breaker.getState().name());
        s.put("failureRatePercent", m.getFailureRate());
        s.put("bufferedCalls", m.getNumberOfBufferedCalls());
        s.put("failedCalls", m.getNumberOfFailedCalls());
        s.put("notPermittedCalls", m.getNumberOfNotPermittedCalls());
        return s;
    }

    private <T> T call(Supplier<T> remote) {
        Supplier<T> translated = () -> {
            try {
                return remote.get();
            } catch (FeignException fe) {
                if (FeignErrors.isClientError(fe)) {
                    throw FeignErrors.toApi(fe);
                }
                throw fe;
            }
        };
        Supplier<T> guarded = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(breaker, translated));
        try {
            return guarded.get();
        } catch (ApiException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new WalletUnavailableException("Wallet Service circuit is OPEN - failing fast, retry shortly");
        } catch (RuntimeException e) {
            throw new WalletUnavailableException("Wallet Service unavailable (" + e.getClass().getSimpleName() + ")");
        }
    }
}
