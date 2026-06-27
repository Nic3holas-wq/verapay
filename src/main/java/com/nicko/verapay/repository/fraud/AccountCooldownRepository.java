package com.nicko.verapay.repository.fraud;

import com.nicko.verapay.entity.fraud.AccountCooldown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AccountCooldownRepository extends JpaRepository<AccountCooldown, Long> {
    Optional<AccountCooldown> findFirstByUserIdAndIsActiveTrueAndCooldownExpiresAtAfter(Long userId, LocalDateTime now);
}
