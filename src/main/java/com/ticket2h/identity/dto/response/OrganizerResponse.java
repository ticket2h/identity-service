package com.ticket2h.identity.dto.response;

import java.util.UUID;

import com.ticket2h.identity.entity.Organizer;

public record OrganizerResponse(UUID id, String name) {

    public static OrganizerResponse of(Organizer organizer) {
        return new OrganizerResponse(organizer.getId(), organizer.getName());
    }
}
