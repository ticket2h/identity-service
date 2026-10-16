package com.ticket2h.identity.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket2h.identity.entity.Organizer;

public interface OrganizerRepository extends JpaRepository<Organizer, UUID> {}
