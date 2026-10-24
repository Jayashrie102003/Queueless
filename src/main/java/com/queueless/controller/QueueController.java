package com.queueless.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.QueueService;
import com.queueless.service.QueueService.QueueSummary;
import com.queueless.service.QueueTokenService;
import com.queueless.service.QueueTokenService.TokenView;
import com.queueless.service.SessionUtil;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueService queueService;
    private final QueueTokenService tokenService;

    public QueueController(QueueService queueService, QueueTokenService tokenService) {
        this.queueService = queueService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public List<QueueSummary> list() {
        return queueService.listActive();
    }

    @PostMapping("/{id}/join")
    public TokenView join(@PathVariable("id") Long id, HttpSession session) {
        Long userId = SessionUtil.requireUserId(session);
        return tokenService.join(userId, id);
    }
}