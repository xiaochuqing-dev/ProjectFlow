package com.projectflow.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projectflow.dto.AuthDtos.AuthResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.AuthDtos.LoginRequest;
import com.projectflow.dto.AuthDtos.RegisterRequest;
import com.projectflow.entity.User;
import com.projectflow.repository.UserRepository;
import com.projectflow.security.JwtService;
import com.projectflow.support.AppException;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new AppException("EMAIL_ALREADY_REGISTERED", "Email is already registered", HttpStatus.CONFLICT);
        }

        User user = userRepository.save(new User(
            request.username().trim(),
            email,
            passwordEncoder.encode(request.password())
        ));
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AppException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthUser currentUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AppException("UNAUTHORIZED", "Missing bearer token", HttpStatus.UNAUTHORIZED);
        }

        try {
            String token = authorizationHeader.substring("Bearer ".length()).trim();
            User user = userRepository.findById(jwtService.parseUserId(token))
                .orElseThrow(() -> new AppException("UNAUTHORIZED", "Invalid bearer token", HttpStatus.UNAUTHORIZED));
            return new AuthUser(user.getId(), user.getUsername(), user.getEmail());
        } catch (RuntimeException exception) {
            if (exception instanceof AppException appException) {
                throw appException;
            }
            throw new AppException("UNAUTHORIZED", "Invalid bearer token", HttpStatus.UNAUTHORIZED);
        }
    }

    private AuthResponse responseFor(User user) {
        return new AuthResponse(
            jwtService.createAccessToken(user.getId(), user.getEmail()),
            new AuthUser(user.getId(), user.getUsername(), user.getEmail())
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
