package com.demo.auth.config;

import com.demo.auth.model.AuthUser;
import com.demo.auth.repository.AuthUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one ready-to-use demo account on startup (username: demo,
 * password: Demo@1234) so you can test the login flow immediately
 * without registering a user first. Uses the real PasswordEncoder bean
 * rather than a pre-computed hash in a SQL script, since that's the
 * safer/clearer way to seed a hashed password.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "Demo@1234";
    private static final String DEMO_ROLE = "USER";

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!authUserRepository.existsByUsername(DEMO_USERNAME)) {
            authUserRepository.save(new AuthUser(
                    DEMO_USERNAME,
                    passwordEncoder.encode(DEMO_PASSWORD),
                    DEMO_ROLE
            ));
        }
    }
}
