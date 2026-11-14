package com.azentio.clearlist.controller;

import com.azentio.clearlist.dto.CurrentUserResponse;
import com.azentio.clearlist.security.Role;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets a client verify credentials and learn the caller's roles (e.g. to show role-specific actions). */
@RestController
public class CurrentUserController {

    @GetMapping("/api/me")
    public CurrentUserResponse me(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName(), Role.of(authentication));
    }
}
