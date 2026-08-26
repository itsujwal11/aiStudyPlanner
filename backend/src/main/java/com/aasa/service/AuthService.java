package com.aasa.service;

import com.aasa.entity.User;
import com.aasa.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Minimal authentication-support service. Account registration, login and
 * password flows are handled by {@link AccountAuthService}; this class only
 * exposes helper lookups used by controllers to resolve the authenticated
 * user from the JWT subject.
 *
 * <p>Formerly this class auto-seeded a predictable default administrator
 * (admin@aasa.com / admin123) on every startup via an
 * {@code ApplicationReadyEvent} listener, and exposed register/login/seedAdmin
 * helpers. Those were removed because a well-known default admin is a security
 * risk in any real deployment — an administrator should instead be created via
 * a controlled, environment-driven provisioning step.</p>
 */
@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}