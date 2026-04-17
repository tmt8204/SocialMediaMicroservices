package com.socialmedia.admin.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.admin.entities.jpa.CommunityEntity;

public interface CommunityAdminRepository extends JpaRepository<CommunityEntity, Long> {
}
