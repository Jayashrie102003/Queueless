package com.queueless.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.queueless.model.QueueToken;

public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {
}