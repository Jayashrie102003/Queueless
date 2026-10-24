package com.queueless.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.QueueTokenService;
import com.queueless.service.QueueTokenService.TokenView;
import com.queueless.service.SessionUtil;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final QueueTokenService tokenService;

    public TokenController(QueueTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/me")
    public List<TokenView> myTokens(HttpSession session) {
        Long userId = SessionUtil.requireUserId(session);
        return tokenService.myActiveTokens(userId);
    }

    @PostMapping("/{id}/leave")
    public Map<String, String> leave(@PathVariable("id") Long id, HttpSession session) {
        Long userId = SessionUtil.requireUserId(session);
        tokenService.leave(userId, id);
        return Map.of("status", "cancelled");
    }
}