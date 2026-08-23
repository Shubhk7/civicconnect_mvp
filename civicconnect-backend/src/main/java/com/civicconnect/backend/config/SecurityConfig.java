package com.civicconnect.backend.config;

import com.civicconnect.backend.config.SecurityJsonHandlers.JsonAccessDeniedHandler;
import com.civicconnect.backend.config.SecurityJsonHandlers.JsonAuthenticationEntryPoint;
import com.civicconnect.backend.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
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

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            CorsConfigurationSource corsConfigurationSource,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
            // Same CORS rules used everywhere else in the app (see
            // CorsConfig) — must be wired in here too, since Security's
            // filters run before MVC's own CORS handling and would
            // otherwise reject the WordPress frontend's preflight requests.
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // CSRF protection is for cookie-based session auth; this API
            // is stateless and JWT-bearer-token-based, so there's no
            // session cookie for an attacker to ride on.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                // Preflight requests never carry auth headers — must be
                // allowed through unconditionally or CORS breaks entirely.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // --- Public: no account needed, matches product requirement
                //     that reporting never requires login ---
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/complaints").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/wards").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/uploads/photo").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                // Public feed for logged-out views (homepage ledger etc).
                // Must be registered before the /api/complaints/* and
                // /api/complaints matchers below, since Spring Security
                // uses first-match-wins and both of those would otherwise
                // shadow this more specific path.
                .requestMatchers(HttpMethod.GET, "/api/complaints/public").permitAll()

                // Public: explicit upvote on an existing report. No
                // account required — see ComplaintController#upvote for
                // how anonymous voters are (loosely) deduplicated.
                .requestMatchers(HttpMethod.POST, "/api/complaints/*/upvote").permitAll()

                // --- Authenticated: any logged-in role, own-data only
                //     (ownership is enforced in the controller, not here) ---
                .requestMatchers(HttpMethod.GET, "/api/complaints/my").authenticated()

                // --- Public: single complaint + its timeline. Sanitized
                //     at the DTO level (see PublicTimelineEntry) so this
                //     being public is safe. Must be registered AFTER
                //     /api/complaints/my above, since both match under
                //     /api/complaints/*, and Spring Security uses the
                //     first matching rule. ---
                .requestMatchers(HttpMethod.GET, "/api/complaints/*").permitAll()

                // --- Officer/admin only ---
                .requestMatchers(HttpMethod.GET, "/api/complaints").hasAnyAuthority("ROLE_OFFICER", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/complaints/*/resolve").hasAnyAuthority("ROLE_OFFICER", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/complaints/*/status").hasAnyAuthority("ROLE_OFFICER", "ROLE_ADMIN")

                // Safe default: anything not explicitly listed above
                // requires a logged-in user rather than being open by
                // accident.
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
