package com.wanderly.api.service;

import com.wanderly.api.domain.Enums.NotificationType;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.*;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wanderly.api.security.JwtService;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final NotificationService notifications;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, NotificationService notifications) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.notifications = notifications;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(req.name().trim(), email, encoder.encode(req.password())));
        notifications.notify(user, NotificationType.SYSTEM, "Welcome to Wanderly, " + user.getName().split(" ")[0] + "!",
                "Start by exploring destinations or ask the AI assistant to plan your first trip.");
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds(), UserDto.from(user));
    }
}
