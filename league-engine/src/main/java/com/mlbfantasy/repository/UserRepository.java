package com.mlbfantasy.repository;

import com.mlbfantasy.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByDisplayName(String displayName);

    boolean existsByEmail(String email);

    boolean existsByDisplayName(String displayName);
}
