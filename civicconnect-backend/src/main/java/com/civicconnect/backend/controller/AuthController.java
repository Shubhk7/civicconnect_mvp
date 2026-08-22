package com.civicconnect.backend.controller;

import com.civicconnect.backend.dto.AuthDtos.LoginRequest;
import com.civicconnect.backend.dto.AuthDtos.RegisterRequest;
import com.civicconnect.backend.dto.AuthDtos.UserResponse;
import com.civicconnect.backend.model.User;
import com.civicconnect.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = authService.login(req);
        // NOTE for MVP: this returns the user's own profile as
        // confirmation of successful login. It does NOT issue a session
        // token or JWT yet — every subsequent request is still
        // unauthenticated. That's fine for a hackathon demo where you
        // control all traffic, but before any real deployment this needs
        // token-based auth (e.g. JWT) so the backend can verify who is
        // making each later request, not just the login request itself.
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<?> handleAuthException(AuthService.AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(e.getMessage()));
    }

    private record ErrorBody(String error) {}
}
