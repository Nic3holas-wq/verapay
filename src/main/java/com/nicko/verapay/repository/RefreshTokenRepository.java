package com.nicko.verapay.repository;

import com.nicko.verapay.entity.RefreshToken;
import com.nicko.verapay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    // Revoke all tokens for a user (used on logout or reuse detection)
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = CURRENT_TIMESTAMP WHERE r.user = :user")
    void revokeAllUserTokens(User user);


    // Check if a previous token exists (reuse detection)
    Optional<RefreshToken> findByPreviousToken(String previousToken);


}