package com.competeleak.summarizer.filter;

import com.competeleak.summarizer.model.User;
import com.competeleak.summarizer.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Reads the X-Api-Key header on each request.
 * If present and valid, attaches the resolved User to the request as an attribute.
 * Does NOT reject requests without a key — the service layer enforces free-tier limits.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "authenticatedUser";

    private final UserRepository userRepository;

    public ApiKeyAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-Api-Key");

        if (apiKey != null && !apiKey.isBlank()) {
            Optional<User> user = userRepository.findByApiKey(apiKey);
            if (user.isPresent()) {
                request.setAttribute(USER_ATTRIBUTE, user.get());
            } else {
                // Key was provided but doesn't match any user — reject immediately
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid API key\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
