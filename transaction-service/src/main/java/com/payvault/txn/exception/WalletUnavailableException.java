package com.payvault.txn.exception;

/** Wallet Service is down, slow, or the circuit breaker is open - outcome unknown, try again later. */
public class WalletUnavailableException extends RuntimeException {
    public WalletUnavailableException(String message) {
        super(message);
    }
}
