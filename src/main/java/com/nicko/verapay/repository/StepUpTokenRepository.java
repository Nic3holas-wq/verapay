package com.nicko.verapay.repository;

import com.nicko.verapay.entity.StepUpToken;
import com.nicko.verapay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StepUpTokenRepository extends JpaRepository<StepUpToken, Long> {

    Optional<StepUpToken> findByToken(String token);

    // Invalidate all step-up tokens for a user
    @Modifying
    @Query("UPDATE StepUpToken s SET s.used = true WHERE s.user = :user")
    void invalidateAllUserStepUpTokens(User user);
}