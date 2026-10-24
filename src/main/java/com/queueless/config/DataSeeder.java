package com.queueless.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.queueless.model.Role;
import com.queueless.model.ServiceQueue;
import com.queueless.model.User;
import com.queueless.repository.ServiceQueueRepository;
import com.queueless.repository.UserRepository;

@Component
public class DataSeeder implements ApplicationRunner {

    private final ServiceQueueRepository queueRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(ServiceQueueRepository queueRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (queueRepository.count() == 0) {
            queueRepository.save(newQueue("General Medicine", 10));
            queueRepository.save(newQueue("Pediatrics", 12));
            queueRepository.save(newQueue("Dental", 15));
        }

        if (!userRepository.existsByEmail("staff1@queueless.local")) {
            User staff = new User();
            staff.setName("Staff One");
            staff.setEmail("staff1@queueless.local");
            staff.setPasswordHash(passwordEncoder.encode("Staff@123"));
            staff.setRole(Role.STAFF);
            userRepository.save(staff);
        }
    }

    private ServiceQueue newQueue(String name, int averageMinutes) {
        ServiceQueue q = new ServiceQueue();
        q.setName(name);
        q.setAverageServiceMinutes(averageMinutes);
        q.setActive(true);
        return q;
    }
}