package com.queueless.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.model.User;
import com.queueless.service.AuthService;
import com.queueless.service.SessionUtil;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password) {}

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {}

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("id", user.getId(), "name", user.getName(), "role", user.getRole().name()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        User user = authService.authenticate(request.email(), request.password());
        session.setAttribute(SessionUtil.USER_ID, user.getId());
        session.setAttribute(SessionUtil.ROLE, user.getRole().name());
        return Map.of("id", user.getId(), "name", user.getName(), "role", user.getRole().name());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        session.invalidate();
        return Map.of("status", "logged out");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Long userId = SessionUtil.requireUserId(session);
        return Map.of("id", userId, "role", session.getAttribute(SessionUtil.ROLE));
    }
}