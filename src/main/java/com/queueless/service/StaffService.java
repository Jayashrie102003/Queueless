package com.queueless.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueless.model.QueueToken;
import com.queueless.model.ServiceQueue;
import com.queueless.model.TokenStatus;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.ServiceQueueRepository;

@Service
public class StaffService {

    // Staff can see patient names. The public display must never use this record.
    public record StaffToken(Long id, int tokenNumber, String patientName, String status) {}

    public record StaffQueueView(String queueName, StaffToken serving, List<StaffToken> waiting) {}

    private final ServiceQueueRepository queueRepository;
    private final QueueTokenRepository tokenRepository;
    private final Clock clock;

    public StaffService(ServiceQueueRepository queueRepository,
                        QueueTokenRepository tokenRepository,
                        Clock clock) {
        this.queueRepository = queueRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StaffQueueView view(Long queueId) {
        ServiceQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Queue not found"));
        LocalDate today = LocalDate.now(clock);

        StaffToken serving = tokenRepository
                .findFirstByQueueIdAndTokenDateAndStatus(queueId, today, TokenStatus.CALLED)
                .map(this::toStaff)
                .orElse(null);

        List<StaffToken> waiting = tokenRepository
                .findByQueueIdAndTokenDateAndStatusOrderByTokenNumberAsc(queueId, today, TokenStatus.WAITING)
                .stream()
                .map(this::toStaff)
                .toList();

        return new StaffQueueView(queue.getName(), serving, waiting);
    }

    @Transactional
    public StaffToken callNext(Long queueId) {
        // Lock the queue so two staff clicks can't call two different patients at once.
        queueRepository.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Queue not found"));
        LocalDate today = LocalDate.now(clock);

        if (tokenRepository.countByQueueIdAndTokenDateAndStatus(queueId, today, TokenStatus.CALLED) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Finish the current patient before calling the next one");
        }

        QueueToken next = tokenRepository
                .findFirstByQueueIdAndTokenDateAndStatusOrderByTokenNumberAsc(queueId, today, TokenStatus.WAITING)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "No one is waiting"));

        next.setStatus(TokenStatus.CALLED);
        next.setCalledAt(LocalDateTime.now(clock));
        return toStaff(next);
    }

    @Transactional
    public StaffToken complete(Long tokenId) {
        QueueToken token = findToken(tokenId);
        if (token.getStatus() != TokenStatus.CALLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only the patient being served can be completed");
        }
        token.setStatus(TokenStatus.COMPLETED);
        token.setCompletedAt(LocalDateTime.now(clock));
        return toStaff(token);
    }

    @Transactional
    public StaffToken markAbsent(Long tokenId) {
        QueueToken token = findToken(tokenId);
        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only waiting or called tokens can be marked absent");
        }
        token.setStatus(TokenStatus.ABSENT);
        return toStaff(token);
    }

    private QueueToken findToken(Long tokenId) {
        return tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Token not found"));
    }

    private StaffToken toStaff(QueueToken token) {
        return new StaffToken(
                token.getId(),
                token.getTokenNumber(),
                token.getUser().getName(),
                token.getStatus().name());
    }
}