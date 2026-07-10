package com.projectflow.service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.projectflow.dto.AuthDtos.AuthResponse;
import com.projectflow.dto.AuthDtos.AuthUser;
import com.projectflow.dto.AuthDtos.LoginRequest;
import com.projectflow.dto.AuthDtos.RegisterRequest;
import com.projectflow.dto.AuthDtos.ResetPasswordRequest;
import com.projectflow.entity.User;
import com.projectflow.repository.UserRepository;
import com.projectflow.security.JwtService;
import com.projectflow.support.AppException;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final int PASSWORD_RESET_ATTEMPT_LIMIT = 5;
    private static final Duration PASSWORD_RESET_ATTEMPT_WINDOW = Duration.ofMinutes(1); // 1 分钟内最多尝试 5 次

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String passwordResetCode;
    private final Instant passwordResetCodeExpiresAt;
    private final Map<String, Deque<Instant>> passwordResetAttempts = new ConcurrentHashMap<>();
    private boolean passwordResetCodeUsed;

    public AuthService(
        UserRepository userRepository,
        JwtService jwtService,
        @Value("${projectflow.auth.password-reset-code:}") String configuredPasswordResetCode,
        @Value("${projectflow.auth.password-reset-code-valid-minutes:30}") int passwordResetCodeValidMinutes
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordResetCode = configuredPasswordResetCode.isBlank()
            ? UUID.randomUUID().toString()
            : configuredPasswordResetCode.trim();
        this.passwordResetCodeExpiresAt = Instant.now().plus(Duration.ofMinutes(passwordResetCodeValidMinutes));
        logger.warn("本次启动的密码重置验证码：{}。验证码仅可使用一次，{} 分钟后失效。", passwordResetCode, passwordResetCodeValidMinutes);
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

    @Transactional
    public synchronized void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        recordPasswordResetAttempt(email);
        if (passwordResetCodeUsed || Instant.now().isAfter(passwordResetCodeExpiresAt)
            || !MessageDigest.isEqual(
                passwordResetCode.getBytes(StandardCharsets.UTF_8),
                request.recoveryCode().trim().getBytes(StandardCharsets.UTF_8)
            )) {
            throw new AppException("INVALID_RECOVERY_CODE", "重置验证码无效或已过期，请从启动 ProjectFlow 的终端获取新的验证码", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AppException("INVALID_RECOVERY_CODE", "重置验证码无效或已过期，请从启动 ProjectFlow 的终端获取新的验证码", HttpStatus.UNAUTHORIZED));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        passwordResetCodeUsed = true;
        passwordResetAttempts.remove(email);
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

    private void recordPasswordResetAttempt(String email) {
        Deque<Instant> attempts = passwordResetAttempts.computeIfAbsent(email, ignored -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant windowStart = now.minus(PASSWORD_RESET_ATTEMPT_WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
            attempts.removeFirst();
        }
        if (attempts.size() >= PASSWORD_RESET_ATTEMPT_LIMIT) {
            throw new AppException("PASSWORD_RESET_RATE_LIMITED", "重置尝试过于频繁，请 1 分钟后再试", HttpStatus.TOO_MANY_REQUESTS);
        }
        attempts.addLast(now);
    }
}
