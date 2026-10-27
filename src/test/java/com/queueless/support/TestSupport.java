package com.queueless.support;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.queueless.model.Role;
import com.queueless.model.User;
import com.queueless.repository.QueueTokenRepository;
import com.queueless.repository.UserRepository;

public final class TestSupport {

    private TestSupport() {
    }

    // Removes all tokens, and all PATIENT accounts. Staff and queues are kept.
    public static void resetPatientsAndTokens(QueueTokenRepository tokens, UserRepository users) {
        tokens.deleteAll();
        users.findAll().stream()
                .filter(u -> u.getRole() == Role.PATIENT)
                .forEach(users::delete);
    }

    // Creates a patient with a unique email, and returns the saved account.
    public static User createPatient(UserRepository users, PasswordEncoder encoder, String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.replace(" ", "").toLowerCase() + "-" + System.nanoTime() + "@loadtest.local");
        user.setPasswordHash(encoder.encode("secret123"));
        user.setRole(Role.PATIENT);
        return users.save(user);
    }
}