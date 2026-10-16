package com.flowboard.security;

import com.flowboard.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer xxx", and if the token is valid puts the user into the SecurityContext.
 *
 * <p>Its only job is to answer "who is this?". An invalid token is NOT rejected here: the request simply stays
 * anonymous, and the authorization rules plus RestAuthenticationEntryPoint turn that into a 401 for protected
 * URLs. One class authenticates, another authorizes.
 *
 * <p>This class is deliberately not a @Component. A Filter bean would also be registered as a plain servlet
 * filter by Spring Boot and run twice per request; SecurityConfig creates it with "new" instead.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Long userId = jwtService.parseUserId(header.substring(BEARER_PREFIX.length()));
                // Also confirm the user still exists (for example the development database was wiped but the
                // browser still holds an old token). Costs one primary-key lookup per request.
                userRepository.findById(userId).ifPresent(user -> {
                    var principal = new AuthUser(user.getId(), user.getUsername());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                });
            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid, expired or tampered token: treat the request as anonymous.
            }
        }
        chain.doFilter(request, response);
    }
}
