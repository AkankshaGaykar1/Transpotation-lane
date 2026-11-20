package com.assignment.transporter.domain;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

// ids come from the client, not the db. without this spring data runs a select before every insert
// to see if the row is new. saying "new" ourselves lets the inserts batch.
@MappedSuperclass
abstract class NewAwareEntity<ID> implements Persistable<ID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
