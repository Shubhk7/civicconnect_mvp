package com.civicconnect.backend.security;

import com.civicconnect.backend.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs on every request, before Spring Security's authorization checks.
 * If a valid "Authorization: Bearer <jwt>" header is present, decodes it
 * and installs a real Authentication (JwtAuthenticationToken) into the
 * SecurityContext, with a ROLE_<role> GrantedAuthority so
 * hasAuthority()/hasRole() rules in SecurityConfig actually work.
 *
 * This filter itself never rejects a request for missing/invalid tokens
 * — most endpoints in this app are intentionally public (anonymous
 * reporting). "No token" or "bad token" both simply mean the
 * SecurityContext stays empty, and SecurityConfig's authorizeHttpRequests
 * rules (plus the custom AuthenticationEntryPoint) are what actually
 * reject access to protected endpoints with a 401/403.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtService.parseAndValidate(token);
                Integer userId = Integer.valueOf(claims.getSubject());
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                Integer wardId = claims.get("wardId", Integer.class);
                AuthenticatedUser authUser = new AuthenticatedUser(userId, username, role, wardId);
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authUser));
            } catch (JwtException | IllegalArgumentException e) {
                // Invalid/expired/tampered token: proceed unauthenticated.
                // SecurityContext stays empty, so anything requiring auth
                // will be rejected downstream by Spring Security itself.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
