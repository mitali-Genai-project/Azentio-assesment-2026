package com.azentio.clearlist.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Passwords for the two in-memory demo users. Supplied via environment variables, never committed. */
@Validated
@ConfigurationProperties(prefix = "clearlist.security")
public record UserCredentialsProperties(@NotBlank String analystPassword, @NotBlank String supervisorPassword) {
}
