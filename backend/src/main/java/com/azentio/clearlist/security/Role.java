package com.azentio.clearlist.security;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public enum Role {
    ANALYST,
    SUPERVISOR;

    public String authority() {
        return "ROLE_" + name();
    }

    /** The Clearlist roles held by an authenticated principal (other authorities are ignored). */
    public static Set<Role> of(Authentication authentication) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            for (Role role : values()) {
                if (role.authority().equals(granted.getAuthority())) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }
}
