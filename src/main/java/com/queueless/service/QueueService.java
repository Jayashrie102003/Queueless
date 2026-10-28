package com.queueless.service;

import java.time.Clock;
import java.time.LocalDate;
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
public class QueueService {

    public record QueueSummary(Long id, String name, int averageServiceMinutes) {}

    // Public view: token numbers only, never patient names.
    public record PublicDisplay(String queueName, Integer nowServing, List<Integer> waiting) {}

    private final ServiceQueueRepository queueRepository;
    private final QueueTokenRepository tokenRepository;
    private final Clock clock;

    public QueueService(ServiceQueueRepository queueRepository,
                        QueueTokenRepository tokenRepository,
                        Clock clock) {
        this.queueRepository = queueRepository;
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<QueueSummary> listActive() {
        return queueRepository.findByActiveTrue().stream()
                .map(q -> new QueueSummary(q.getId(), q.getName(), q.getAverageServiceMinutes()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicDisplay publicDisplay(Long queueId) {
        ServiceQueue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Queue not found"));
        LocalDate today = LocalDate.now(clock);

        Integer nowServing = tokenRepository
                .findFirstByQueueIdAndTokenDateAndStatus(queueId, today, TokenStatus.CALLED)
                .map(QueueToken::getTokenNumber)
                .orElse(null);

        List<Integer> waiting = tokenRepository
                .findByQueueIdAndTokenDateAndStatusOrderByTokenNumberAsc(queueId, today, TokenStatus.WAITING)
                .stream()
                .map(QueueToken::getTokenNumber)
                .toList();

        return new PublicDisplay(queue.getName(), nowServing, waiting);
    }
}
