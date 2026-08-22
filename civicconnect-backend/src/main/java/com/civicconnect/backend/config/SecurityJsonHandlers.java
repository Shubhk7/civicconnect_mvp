package com.civicconnect.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Spring Security's defaults return a blank 401/403 (or an HTML error
 * page) with no body, which is unhelpful for a JSON API and inconsistent
 * with every other error response in this app. These two beans make
 * auth failures look like every other error: a small JSON object.
 */
public class SecurityJsonHandlers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Component
    public static class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                              AuthenticationException authException) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            MAPPER.writeValue(response.getWriter(),
                Map.of("error", "Login required to access this resource."));
        }
    }

    @Component
    public static class JsonAccessDeniedHandler implements AccessDeniedHandler {
        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                            AccessDeniedException accessDeniedException) throws IOException {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            MAPPER.writeValue(response.getWriter(),
                Map.of("error", "You don't have permission to perform this action."));
        }
    }
}
