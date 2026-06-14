package com.nicko.verapay.aspects;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class LoggingAndPerformanceAspect {

    @Around("execution(* com.nicko.verapay..*.*(..))")
    public Object logAndMeasureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();
        Object[] methodArgs = joinPoint.getArgs();

        String maskedArgs = Arrays.stream(methodArgs)
                .map(this::maskSensitiveData)
                .collect(Collectors.joining(", "));

        log.info("➡️ Entering method: {}", methodName);
        log.info("📥 Arguments: [{}]", maskedArgs);

        Object result = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - startTime;
        log.info("✅ Method executed successfully: {}", methodName);
        log.info("⏱ Execution time: {} ms", executionTime);
        return result;
    }

    private String maskSensitiveData(Object arg) {
        if (arg == null) return "null";
        String str = arg.toString();

        str = str.replaceAll("transactionPin=\\d{4}", "transactionPin=****");
        str = str.replaceAll("password=[^,\\]]+", "password=****");
        str = str.replaceAll("token=[a-fA-F0-9]+", "token=****");

        return str;
    }
}