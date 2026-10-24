package com.queueless.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.queueless.model.ServiceQueue;

import jakarta.persistence.LockModeType;

public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, Long> {

    List<ServiceQueue> findByActiveTrue();

    // Locks the queue row until the surrounding transaction ends (used in Part 3).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ServiceQueue q where q.id = :id")
    Optional<ServiceQueue> findByIdForUpdate(@Param("id") Long id);
}