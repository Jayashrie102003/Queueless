package com.queueless.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.queueless.model.ServiceQueue;

public interface ServiceQueueRepository extends JpaRepository<ServiceQueue, Long> {
}