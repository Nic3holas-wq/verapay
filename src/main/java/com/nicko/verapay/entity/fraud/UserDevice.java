package com.nicko.verapay.entity.fraud;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "user_devices")
public class UserDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_fingerprint", nullable = false)
    private String deviceFingerprint;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "location_geo")
    private String locationGeo;

    @Column(name = "user_agent", nullable = false, columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "is_trusted")
    private Boolean isTrusted = false;

    @Column(name = "last_login", nullable = false)
    private LocalDateTime lastLogin = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters, Setters, and Constructors
}
