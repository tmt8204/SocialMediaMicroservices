package com.socialmedia.user_service_social_media.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.socialmedia.user_service_social_media.entities.User;

public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByUserId(String userId);

	Optional<User> findByUsername(String username);

	Optional<User> findByUsernameIgnoreCase(String username);

	@Query("{ '$or': [ { 'username': { '$regex': ?0, '$options': 'i' } }, { 'fullName': { '$regex': ?0, '$options': 'i' } } ] }")
	List<User> searchByUsernameOrFullName(String keyword);

	boolean existsByUsername(String username);

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmail(String email);

	boolean existsByEmailIgnoreCase(String email);
}
