package com.civicconnect.backend.controller;

import com.civicconnect.backend.dto.AuthDtos.AuthResponse;
import com.civicconnect.backend.dto.AuthDtos.LoginRequest;
import com.civicconnect.backend.dto.AuthDtos.RegisterRequest;
import com.civicconnect.backend.dto.AuthDtos.UserResponse;
import com.civicconnect.backend.model.User;
import com.civicconnect.backend.service.AuthService;
import com.civicconnect.backend.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req);
        String token = jwtService.issueToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(UserResponse.from(user), token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = authService.login(req);
        String token = jwtService.issueToken(user);
        return ResponseEntity.ok(new AuthResponse(UserResponse.from(user), token));
    }

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<?> handleAuthException(AuthService.AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(e.getMessage()));
    }

    private record ErrorBody(String error) {}
}
