package com.nicko.verapay.transaction.service;

import com.nicko.verapay.repository.DailySequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class TransactionCodeGeneratorImpl implements TransactionCodeGenerator {

    private final DailySequenceRepository dailySequenceRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "VP-";

    public TransactionCodeGeneratorImpl(DailySequenceRepository dailySequenceRepository) {
        this.dailySequenceRepository = dailySequenceRepository;
    }

    @Override
    @Transactional
    public String generateCode() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Long sequenceValue = dailySequenceRepository.getNextSequenceValue(today);
        return PREFIX + today.format(DATE_FORMATTER) + "-" + String.format("%06d", sequenceValue);
    }
}
