package com.nicko.verapay.repository.fraud;

import com.nicko.verapay.entity.fraud.TransactionRiskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRiskLogRepository extends JpaRepository<TransactionRiskLog, Long> {
}
