package com.civicconnect.backend.service;

import com.civicconnect.backend.dto.AuthDtos.LoginRequest;
import com.civicconnect.backend.dto.AuthDtos.RegisterRequest;
import com.civicconnect.backend.model.User;
import com.civicconnect.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) { super(message); }
    }

    public User register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email)) {
            throw new AuthException("An account with this email already exists");
        }
        if (userRepository.existsByUsername(req.username)) {
            throw new AuthException("This username is taken");
        }

        User user = new User();
        user.setUsername(req.username);
        user.setEmail(req.email);
        user.setPhoneNumber(req.phoneNumber);
        // encode() applies BCrypt with a fresh random salt — the plaintext
        // password itself is never stored or logged anywhere.
        user.setPasswordHash(passwordEncoder.encode(req.password));
        user.setFullName(req.fullName);
        user.setRole("CITIZEN");

        return userRepository.save(user);
    }

    public User login(LoginRequest req) {
        User user = userRepository.findByUsername(req.usernameOrEmail)
            .or(() -> userRepository.findByEmail(req.usernameOrEmail))
            .orElseThrow(() -> new AuthException("Invalid username/email or password"));

        // matches() re-hashes the supplied password with the stored salt
        // and compares — the plaintext password is never compared
        // directly, and timing is constant regardless of match/mismatch.
        if (!passwordEncoder.matches(req.password, user.getPasswordHash())) {
            // Deliberately identical error message to the "user not found"
            // case above — don't let the error reveal whether the
            // username/email exists at all.
            throw new AuthException("Invalid username/email or password");
        }

        return user;
    }
}
