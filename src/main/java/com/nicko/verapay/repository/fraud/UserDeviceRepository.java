package com.nicko.verapay.repository.fraud;

import com.nicko.verapay.entity.fraud.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserIdAndDeviceFingerprint(Long userId, String deviceFingerprint);
}
