package com.socialmedia.auth.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.socialmedia.auth.entities.Role;

public interface RoleRepository extends MongoRepository<Role, String> {

    Optional<Role> findByRoleName(String roleName);
}
