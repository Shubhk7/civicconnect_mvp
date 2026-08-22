package com.civicconnect.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    /**
     * BCrypt: a slow, salted hash purpose-built for passwords — unlike
     * plain SHA-256/MD5, it's deliberately expensive to compute, which is
     * what makes brute-forcing leaked hashes impractical. Every password
     * gets its own random salt automatically, so two users with the same
     * password never produce the same hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
