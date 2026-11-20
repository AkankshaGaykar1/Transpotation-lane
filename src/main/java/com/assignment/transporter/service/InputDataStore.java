package com.assignment.transporter.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.assignment.transporter.domain.LaneEntity;
import com.assignment.transporter.domain.QuoteEntity;
import com.assignment.transporter.domain.TransporterEntity;
import com.assignment.transporter.repository.LaneRepository;
import com.assignment.transporter.repository.QuoteRepository;
import com.assignment.transporter.repository.TransporterRepository;

// holds the one current input. a write replaces everything in one transaction, a read gets a consistent copy.
// the read-write lock stops a reader seeing half a replacement (three tables, read in separate statements)
// and stops two replacements colliding on primary keys. the transaction runs inside the lock (a
// TransactionTemplate, not @Transactional): with the annotation it would commit after the lock is released
// and the next writer could start before that commit. fine for one instance on in-memory h2, with a shared
// database this would need a database lock.
@Service
public class InputDataStore {

    private final LaneRepository lanes;
    private final TransporterRepository transporters;
    private final QuoteRepository quotes;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InputDataStore(LaneRepository lanes, TransporterRepository transporters, QuoteRepository quotes,
                          PlatformTransactionManager transactionManager) {
        this.lanes = lanes;
        this.transporters = transporters;
        this.quotes = quotes;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public void replace(List<LaneEntity> newLanes, List<TransporterEntity> newTransporters,
                        List<QuoteEntity> newQuotes) {
        lock.writeLock().lock();
        try {
            writeTransaction.executeWithoutResult(status -> {
                quotes.deleteAllInBatch();
                transporters.deleteAllInBatch();
                lanes.deleteAllInBatch();
                lanes.saveAll(newLanes);
                transporters.saveAll(newTransporters);
                quotes.saveAll(newQuotes);
                quotes.flush();
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<StoredInput> load() {
        lock.readLock().lock();
        try {
            return readTransaction.execute(status -> {
                List<LaneEntity> storedLanes = lanes.findAll(Sort.by("id"));
                if (storedLanes.isEmpty()) {
                    return Optional.<StoredInput>empty();
                }
                return Optional.of(
                        new StoredInput(storedLanes, transporters.findAll(Sort.by("id")), quotes.findAll()));
            });
        } finally {
            lock.readLock().unlock();
        }
    }
}
