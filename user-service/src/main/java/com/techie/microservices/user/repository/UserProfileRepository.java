package com.techie.microservices.user.repository;

import com.techie.microservices.user.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<UserProfile> findByKeycloakId(UUID keycloakId);
}
