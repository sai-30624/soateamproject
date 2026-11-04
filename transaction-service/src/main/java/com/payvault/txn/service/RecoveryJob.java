package com.payvault.txn.service;

import java.time.Instant;
import java.util.List;

import com.payvault.txn.entity.Transaction;
import com.payvault.txn.entity.TxnStatus;
import com.payvault.txn.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fault tolerance: if Transaction Service crashed or Wallet Service was down mid-transfer,
 * the transaction is left in PENDING / DEBITED / REVERSING. This job finds those and finishes them.
 */
@Component
public class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);

    private final TransactionRepository repo;
    private final TransactionService service;
    private final long stuckAfterSeconds;

    public RecoveryJob(TransactionRepository repo, TransactionService service,
                       @Value("${payvault.recovery.stuck-after-seconds:30}") long stuckAfterSeconds) {
        this.repo = repo;
        this.service = service;
        this.stuckAfterSeconds = stuckAfterSeconds;
    }

    @Scheduled(initialDelayString = "${payvault.recovery.initial-delay-ms:20000}",
            fixedDelayString = "${payvault.recovery.interval-ms:15000}")
    public void recoverStuckTransactions() {
        List<Transaction> stuck = repo.findTop50ByStatusInAndUpdatedAtBeforeOrderByIdAsc(
                List.of(TxnStatus.PENDING, TxnStatus.DEBITED, TxnStatus.REVERSING),
                Instant.now().minusSeconds(stuckAfterSeconds));
        for (Transaction t : stuck) {
            try {
                TxnStatus before = t.getStatus();
                Transaction after = service.runSaga(t);
                if (after.getStatus() != before) {
                    log.info("Recovered {}: {} -> {}", t.getTxnRef(), before, after.getStatus());
                }
            } catch (Exception e) {
                log.warn("Recovery of {} skipped: {}", t.getTxnRef(), e.getMessage());
            }
        }
    }
}
