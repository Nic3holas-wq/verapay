package com.nicko.verapay.repository;

import com.nicko.verapay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Used to prevent duplicate registrations
    boolean existsByEmail(String email);

    // Often needed for login/authentication
    Optional<User> findByEmail(String email);

    Optional<User> readUserByEmailOrPhoneNumber(String email, String phoneNumber);

}