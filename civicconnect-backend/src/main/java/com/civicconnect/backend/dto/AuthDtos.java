package com.civicconnect.backend.dto;

import com.civicconnect.backend.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public static class RegisterRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        public String username;

        @NotBlank
        @Email
        public String email;

        @NotBlank
        public String phoneNumber;

        @NotBlank
        @Size(min = 8, message = "Password must be at least 8 characters")
        public String password;

        public String fullName;
    }

    public static class LoginRequest {
        @NotBlank
        public String usernameOrEmail;

        @NotBlank
        public String password;
    }

    // Explicitly whitelisted fields only — this is what makes it safe to
    // return from an endpoint. passwordHash is never referenced here, so
    // it is structurally impossible for this DTO to leak it, even by
    // future accident.
    public static class UserResponse {
        public Integer id;
        public String username;
        public String email;
        public String phoneNumber;
        public String fullName;
        public String role;

        public static UserResponse from(User u) {
            UserResponse r = new UserResponse();
            r.id = u.getId();
            r.username = u.getUsername();
            r.email = u.getEmail();
            r.phoneNumber = u.getPhoneNumber();
            r.fullName = u.getFullName();
            r.role = u.getRole();
            return r;
        }
    }

    // Returned by /register and /login. Wraps the profile plus the JWT
    // the frontend must store and send back as "Authorization: Bearer
    // <token>" on any request that needs to know who the user is.
    public static class AuthResponse {
        public UserResponse user;
        public String token;

        public AuthResponse(UserResponse user, String token) {
            this.user = user;
            this.token = token;
        }
    }
}
