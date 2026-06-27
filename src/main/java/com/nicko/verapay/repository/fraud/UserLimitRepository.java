package com.nicko.verapay.repository.fraud;

import com.nicko.verapay.entity.fraud.UserLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserLimitRepository extends JpaRepository<UserLimit, Long> {
    Optional<UserLimit> findByUserId(Long userId);
}
