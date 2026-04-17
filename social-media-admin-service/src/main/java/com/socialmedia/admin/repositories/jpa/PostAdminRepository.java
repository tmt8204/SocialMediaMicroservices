package com.socialmedia.admin.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.admin.entities.jpa.PostEntity;

public interface PostAdminRepository extends JpaRepository<PostEntity, Long> {
}
