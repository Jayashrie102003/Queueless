package com.queueless;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.queueless.model.ServiceQueue;
import com.queueless.model.TokenStatus;
import com.queueless.model.User;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.ServiceQueueRepository;
import com.queueless.repository.UserRepository;
import com.queueless.service.ApiException;
import com.queueless.service.QueueTokenService;
import com.queueless.support.TestSupport;

@SpringBootTest
@ActiveProfiles({"test", "test-local"})
class ConcurrencyTest {

    @Autowired
    private QueueTokenService tokenService;
    @Autowired
    private QueueTokenRepository tokens;
    @Autowired
    private UserRepository users;
    @Autowired
    private ServiceQueueRepository queues;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        TestSupport.resetPatientsAndTokens(tokens, users);
    }

    @Test
    void fiftyDifferentPatientsGetFiftyDifferentTokenNumbers() throws Exception {
        final int patients = 50;
        ServiceQueue queue = queues.findAll().get(0);

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < patients; i++) {
            userIds.add(TestSupport.createPatient(users, encoder, "Load " + i).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(patients);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (Long userId : userIds) {
            results.add(pool.submit(joinTask(userId, queue.getId(), startGate)));
        }
        startGate.countDown();

        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : results) {
            outcomes.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        List<Integer> numbers = new ArrayList<>();
        List<Object> refusals = new ArrayList<>();
        for (Object outcome : outcomes) {
            if (outcome instanceof Integer number) {
                numbers.add(number);
            } else {
                refusals.add(outcome);
            }
        }

        assertThat(refusals).as("different patients must all get a token").isEmpty();
        assertThat(numbers).hasSize(patients);
        assertThat(new HashSet<>(numbers)).as("no two patients share a token number").hasSize(patients);
        assertThat(Collections.min(numbers)).isEqualTo(1);
        assertThat(Collections.max(numbers)).isEqualTo(patients);
        assertThat(tokens.count()).isEqualTo(patients);
    }

    @Test
    void oneHundredPercentOfDuplicateJoinsFromOnePatientGiveExactlyOneToken() throws Exception {
        final int attempts = 10;
        ServiceQueue queue = queues.findAll().get(0);
        User patient = TestSupport.createPatient(users, encoder, "Same Patient");

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(joinTask(patient.getId(), queue.getId(), startGate)));
        }
        startGate.countDown();

        int successes = 0;
        for (Future<Object> future : results) {
            if (future.get(60, TimeUnit.SECONDS) instanceof Integer) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).as("only one join may succeed").isEqualTo(1);
        long active = tokens.countActiveForUserInQueue(
                patient.getId(), queue.getId(),
                LocalDate.now(ZoneId.of("Asia/Kolkata")),
                List.of(TokenStatus.WAITING, TokenStatus.CALLED));
        assertThat(active).isEqualTo(1);
    }

    // One join attempt. Returns the token number on success, or the refusal message.
    // Any other exception is left to fail the test, so real server errors are not hidden.
    private Callable<Object> joinTask(Long userId, Long queueId, CountDownLatch startGate) {
        return () -> {
            startGate.await();
            try {
                return tokenService.join(userId, queueId).tokenNumber();
            } catch (ApiException refused) {
                return refused.getMessage();
            }
        };
    }
}