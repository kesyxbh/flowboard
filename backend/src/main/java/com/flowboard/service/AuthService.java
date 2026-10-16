package com.flowboard.service;

import com.flowboard.domain.User;
import com.flowboard.repository.UserRepository;
import com.flowboard.security.JwtService;
import com.flowboard.web.dto.AuthResponse;
import com.flowboard.web.dto.LoginRequest;
import com.flowboard.web.dto.RegisterRequest;
import com.flowboard.web.dto.UserResponse;
import com.flowboard.web.error.ConflictException;
import com.flowboard.web.error.NotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is already taken");
        }
        User user = userRepository.save(
                new User(request.username(), email, passwordEncoder.encode(request.password())));
        return new AuthResponse(jwtService.generateToken(user.getId()), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // An unknown e-mail and a wrong password give the same error, so the endpoint cannot be used to find out
        // which e-mail addresses are registered.
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new AuthResponse(jwtService.generateToken(user.getId()), UserResponse.from(user));
    }

    /** The signed-in user. The frontend calls this on page load to find out whether its stored token is still valid. */
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
