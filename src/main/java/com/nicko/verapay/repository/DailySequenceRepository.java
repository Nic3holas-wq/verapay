package com.nicko.verapay.repository;

import com.nicko.verapay.entity.DailySequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DailySequenceRepository extends JpaRepository<DailySequence, LocalDate> {

    @Query(value = "INSERT INTO daily_sequences (sequence_date, current_value) " +
                   "VALUES (:date, 1) " +
                   "ON CONFLICT (sequence_date) " +
                   "DO UPDATE SET current_value = daily_sequences.current_value + 1 " +
                   "RETURNING current_value", nativeQuery = true)
    Long getNextSequenceValue(@Param("date") LocalDate date);
}
