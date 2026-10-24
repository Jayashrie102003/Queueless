package com.queueless.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.QueueService;
import com.queueless.service.QueueService.PublicDisplay;

@RestController
@RequestMapping("/api/display")
public class DisplayController {

    private final QueueService queueService;

    public DisplayController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/queues/{queueId}")
    public PublicDisplay display(@PathVariable("queueId") Long queueId) {
        return queueService.publicDisplay(queueId);
    }
}