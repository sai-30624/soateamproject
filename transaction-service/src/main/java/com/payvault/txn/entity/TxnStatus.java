package com.payvault.txn.entity;

/**
 * Saga states.
 * PENDING   -> nothing moved yet
 * DEBITED   -> sender debited, receiver not yet credited
 * SUCCESS   -> both legs done
 * FAILED    -> rejected before any money moved (e.g. insufficient balance)
 * REVERSING -> credit failed, refund to sender in progress
 * REVERSED  -> sender refunded; transfer cancelled
 */
public enum TxnStatus {
    PENDING, DEBITED, SUCCESS, FAILED, REVERSING, REVERSED;

    public boolean isInProgress() {
        return this == PENDING || this == DEBITED || this == REVERSING;
    }
}
