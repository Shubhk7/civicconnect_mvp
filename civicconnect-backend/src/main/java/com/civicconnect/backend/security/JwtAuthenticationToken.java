package com.civicconnect.backend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Spring Security Authentication implementation whose principal is our
 * AuthenticatedUser record (decoded, verified JWT claims) rather than a
 * UserDetails loaded from the DB — we don't need a DB round-trip per
 * request since the JWT itself already carries everything we trust.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;

    public JwtAuthenticationToken(AuthenticatedUser principal) {
        super(authorities(principal));
        this.principal = principal;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(AuthenticatedUser user) {
        // Spring Security's hasAuthority("ROLE_X") / hasRole("X") convention.
        String role = user.role() != null ? user.role() : "CITIZEN";
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public Object getCredentials() {
        return null; // the JWT itself; nothing left to check post-verification
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
