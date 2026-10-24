package com.queueless.service;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpSession;

public final class SessionUtil {

    public static final String USER_ID = "userId";
    public static final String ROLE = "role";

    private SessionUtil() {
    }

    public static Long requireUserId(HttpSession session) {
        Object id = session.getAttribute(USER_ID);
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in first");
        }
        return (Long) id;
    }

    public static void requireStaff(HttpSession session) {
        requireUserId(session);
        if (!"STAFF".equals(session.getAttribute(ROLE))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Staff access only");
        }
    }
}