package com.ticket2h.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ticket2h.identity.entity.Organizer;

public interface OrganizerRepository extends JpaRepository<Organizer, UUID> {

    /** Mọi ban tổ chức, theo tên không phân biệt hoa thường; trùng tên thì theo id để thứ tự luôn cố định. */
    @Query("SELECT o FROM Organizer o ORDER BY LOWER(o.name), o.id")
    List<Organizer> findAllOrderByName();
}
