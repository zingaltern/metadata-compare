package com.dqc.compare.api.rest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 当前登录用户（供前端展示用户名与退出入口）。
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public Map<String, String> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
        return Map.of("username", name);
    }
}
