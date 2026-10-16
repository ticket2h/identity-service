package com.ticket2h.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizerRequest(@NotBlank @Size(max = 200) String name) {}
