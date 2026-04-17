package com.socialmedia.admin.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.admin.entities.jpa.CommentEntity;

public interface CommentAdminRepository extends JpaRepository<CommentEntity, Long> {
}
