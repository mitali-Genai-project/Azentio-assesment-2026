package com.azentio.clearlist.dto;

import com.azentio.clearlist.security.Role;
import java.util.Set;

public record CurrentUserResponse(String username, Set<Role> roles) {
}
