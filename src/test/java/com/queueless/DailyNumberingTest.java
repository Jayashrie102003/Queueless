package com.queueless;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.queueless.model.User;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.UserRepository;
import com.queueless.service.QueueTokenService;
import com.queueless.support.MutableClock;
import com.queueless.support.TestSupport;

@SpringBootTest
@ActiveProfiles({"test", "test-local"})
class DailyNumberingTest {

    // Replaces the normal clock with one the test can move.
    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MutableClock movableClock() {
            // 26 October 2026, 09:30 in India.
            return new MutableClock(Instant.parse("2026-10-26T04:00:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    @Autowired
    private QueueTokenService tokenService;
    @Autowired
    private MutableClock clock;
    @Autowired
    private UserRepository users;
    @Autowired
    private QueueTokenRepository tokens;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.parse("2026-10-26T04:00:00Z"));
        TestSupport.resetPatientsAndTokens(tokens, users);
    }

    @Test
    void numberingRestartsAtOneOnANewDay() {
        User a = TestSupport.createPatient(users, encoder, "Day One A");
        User b = TestSupport.createPatient(users, encoder, "Day One B");
        User c = TestSupport.createPatient(users, encoder, "Day Two C");

        assertThat(tokenService.join(a.getId(), 1L).tokenNumber()).isEqualTo(1);
        assertThat(tokenService.join(b.getId(), 1L).tokenNumber()).isEqualTo(2);

        // Move to 27 October, 09:30 in India.
        clock.setInstant(Instant.parse("2026-10-27T04:00:00Z"));

        assertThat(tokenService.join(c.getId(), 1L).tokenNumber()).isEqualTo(1);
    }

    @Test
    void aPatientWhoWaitedYesterdayCanJoinTodayAgain() {
        User a = TestSupport.createPatient(users, encoder, "Carry Over");

        assertThat(tokenService.join(a.getId(), 1L).tokenNumber()).isEqualTo(1);

        clock.setInstant(Instant.parse("2026-10-27T04:00:00Z"));

        // Yesterday's WAITING token does not block today's join.
        assertThat(tokenService.join(a.getId(), 1L).tokenNumber()).isEqualTo(1);
    }
}