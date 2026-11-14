package com.azentio.clearlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ScreeningRequest(
        @NotBlank(message = "customerRef is required") @Size(max = 64) String customerRef,
        @NotBlank(message = "fullName is required") @Size(max = 255) String fullName,
        @Past(message = "dob must be in the past") LocalDate dob,
        @Pattern(regexp = "^[A-Z]{2}$", message = "nationality must be an ISO 3166-1 alpha-2 code, e.g. RU")
        String nationality) {
}
