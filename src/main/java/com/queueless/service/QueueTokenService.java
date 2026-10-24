package com.queueless.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.queueless.model.QueueToken;
import com.queueless.model.ServiceQueue;
import com.queueless.model.TokenStatus;
import com.queueless.model.User;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.ServiceQueueRepository;
import com.queueless.repository.UserRepository;

@Service
public class QueueTokenService {

    // What a patient sees about one of their tokens.
    public record TokenView(Long id, String queueName, int tokenNumber, String status,
                            int peopleAhead, int estimatedWaitMinutes) {}

    private static final List<TokenStatus> ACTIVE = List.of(TokenStatus.WAITING, TokenStatus.CALLED);

    private final ServiceQueueRepository queueRepository;
    private final QueueTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public QueueTokenService(ServiceQueueRepository queueRepository,
                             QueueTokenRepository tokenRepository,
                             UserRepository userRepository,
                             Clock clock) {
        this.queueRepository = queueRepository;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public TokenView join(Long userId, Long queueId) {
        // 1. Lock the queue row. Other joins for this queue wait here until we finish.
        ServiceQueue queue = queueRepository.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Queue not found"));
        if (!queue.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "This queue is closed");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in again"));
        LocalDate today = LocalDate.now(clock);

        // 2. Duplicate check, done AFTER the lock so two requests can't both pass it.
        if (tokenRepository.countActiveForUserInQueue(userId, queueId, today, ACTIVE) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have an active token in this queue");
        }

        // 3. Next number for this queue today.
        int next = tokenRepository.findMaxTokenNumber(queueId, today) + 1;

        // 4. Save. The unique constraint is the second safety net.
        QueueToken token = new QueueToken();
        token.setQueue(queue);
        token.setUser(user);
        token.setTokenDate(today);
        token.setTokenNumber(next);
        token.setStatus(TokenStatus.WAITING);
        token.setCreatedAt(LocalDateTime.now(clock));

        try {
            QueueToken saved = tokenRepository.saveAndFlush(token);
            return toView(saved, today);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Could not get a token, please try again");
        }
    }

    @Transactional(readOnly = true)
    public List<TokenView> myActiveTokens(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return tokenRepository.findActiveForUser(userId, today, ACTIVE).stream()
                .map(t -> toView(t, today))
                .toList();
    }

    @Transactional
    public void leave(Long userId, Long tokenId) {
        QueueToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Token not found"));
        // Don't reveal other people's tokens: treat them as not found.
        if (!token.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Token not found");
        }
        if (token.getStatus() != TokenStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a waiting token can be cancelled");
        }
        token.setStatus(TokenStatus.CANCELLED);
    }

    // Wait-time rule from the spec: people ahead = waiting tokens with a lower number,
    // plus 1 if someone is being served now. Wait = people ahead x average minutes.
    private TokenView toView(QueueToken token, LocalDate today) {
        int peopleAhead = 0;
        int waitMinutes = 0;

        if (token.getStatus() == TokenStatus.WAITING) {
            Long queueId = token.getQueue().getId();
            long waitingBefore = tokenRepository.countBeforeNumberWithStatus(
                    queueId, today, TokenStatus.WAITING, token.getTokenNumber());
            boolean someoneBeingServed = tokenRepository
                    .countByQueueIdAndTokenDateAndStatus(queueId, today, TokenStatus.CALLED) > 0;

            peopleAhead = (int) waitingBefore + (someoneBeingServed ? 1 : 0);
            waitMinutes = peopleAhead * token.getQueue().getAverageServiceMinutes();
        }

        return new TokenView(
                token.getId(),
                token.getQueue().getName(),
                token.getTokenNumber(),
                token.getStatus().name(),
                peopleAhead,
                waitMinutes);
    }
}