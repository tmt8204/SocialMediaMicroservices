package com.socialmedia.auth.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.socialmedia.auth.entities.User;

public interface UserRepository extends MongoRepository<User, String>  {

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);
}
