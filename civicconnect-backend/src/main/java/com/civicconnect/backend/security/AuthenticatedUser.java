package com.civicconnect.backend.security;

/**
 * The identity extracted from a validated JWT for the current request.
 * Never built from anything the client claims directly — only ever
 * constructed from claims that have already passed signature
 * verification in JwtAuthFilter.
 */
public record AuthenticatedUser(Integer userId, String username, String role, Integer wardId) {
}
