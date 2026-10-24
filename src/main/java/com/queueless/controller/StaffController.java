package com.queueless.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.queueless.service.SessionUtil;
import com.queueless.service.StaffService;
import com.queueless.service.StaffService.StaffQueueView;
import com.queueless.service.StaffService.StaffToken;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping("/queues/{queueId}")
    public StaffQueueView view(@PathVariable("queueId") Long queueId, HttpSession session) {
        SessionUtil.requireStaff(session);
        return staffService.view(queueId);
    }

    @PostMapping("/queues/{queueId}/call-next")
    public StaffToken callNext(@PathVariable("queueId") Long queueId, HttpSession session) {
        SessionUtil.requireStaff(session);
        return staffService.callNext(queueId);
    }

    @PostMapping("/tokens/{tokenId}/complete")
    public StaffToken complete(@PathVariable("tokenId") Long tokenId, HttpSession session) {
        SessionUtil.requireStaff(session);
        return staffService.complete(tokenId);
    }

    @PostMapping("/tokens/{tokenId}/absent")
    public StaffToken markAbsent(@PathVariable("tokenId") Long tokenId, HttpSession session) {
        SessionUtil.requireStaff(session);
        return staffService.markAbsent(tokenId);
    }
}