package com.nicko.verapay.repository;

import com.nicko.verapay.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> readUserByEmailOrPhoneNumber(String email, String phoneNumber);
    Optional<User> findByPublicId(UUID publicId);

    // Search/filter for admin
    @Query("SELECT u FROM User u WHERE " +
            "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%'))) " +
            "AND (:isActive IS NULL OR u.isActive = :isActive)")
    Page<User> searchUsers(@Param("email") String email,
                           @Param("isActive") Boolean isActive,
                           Pageable pageable);
}