package com.nicko.verapay.repository.fraud;

import com.nicko.verapay.entity.fraud.KnownBadIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnownBadIpRepository extends JpaRepository<KnownBadIp, Long> {
    boolean existsByIpAddress(String ipAddress);
}
