package com.nicko.verapay.admin.service;

import com.nicko.verapay.constants.ApplicationConstants;
import com.nicko.verapay.dto.admin.UserAdminDto;
import com.nicko.verapay.dto.admin.UserDetailAdminDto;
import com.nicko.verapay.entity.Role;
import com.nicko.verapay.entity.User;
import com.nicko.verapay.entity.Wallet;
import com.nicko.verapay.exception.UserNotFoundException;
import com.nicko.verapay.repository.TransactionRepository;
import com.nicko.verapay.repository.UserRepository;
import com.nicko.verapay.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User user;
    private Wallet wallet;
    private UUID publicId;

    @BeforeEach
    void setUp() {
        publicId = UUID.randomUUID();

        Role role = new Role();
        role.setName(ApplicationConstants.ROLE_CUSTOMER);

        user = new User();
        user.setId(1L);
        user.setPublicId(publicId);
        user.setFullName("Nicholas");
        user.setEmail("nicholas@e.com");
        user.setPhoneNumber("0712345678");
        user.setRole(role);
        user.setIsActive(true);
        user.setCreatedAt(Instant.parse("2026-06-01T12:00:00Z"));

        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setPublicId(UUID.randomUUID());
        wallet.setOwner(user);
        wallet.setBalance(new BigDecimal("5000.00"));
        wallet.setCurrency("KES");
        wallet.setIsActive(true);
    }

    @Test
    @DisplayName("getAllUsers: should return paginated users mapped to UserAdminDto")
    void getAllUsers_Success() {
        Page<User> userPage = new PageImpl<>(List.of(user));
        when(userRepository.searchUsers(eq("nicholas@e.com"), eq(true), any(PageRequest.class)))
                .thenReturn(userPage);

        Page<UserAdminDto> result = adminUserService.getAllUsers("nicholas@e.com", true, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        UserAdminDto dto = result.getContent().get(0);
        assertEquals(publicId, dto.publicId());
        assertEquals("Nicholas", dto.fullName());
    }

    @Test
    @DisplayName("getUserDetail: should return details of user and wallet by publicId")
    void getUserDetail_Success() {
        when(userRepository.findByPublicId(publicId)).thenReturn(Optional.of(user));
        when(walletRepository.findByOwnerEmail(user.getEmail())).thenReturn(Optional.of(wallet));
        when(transactionRepository.findRecentByWallet(eq(wallet), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        UserDetailAdminDto result = adminUserService.getUserDetail(publicId);

        assertNotNull(result);
        assertEquals(publicId, result.publicId());
        assertEquals("Nicholas", result.fullName());
        assertEquals(new BigDecimal("5000.00"), result.walletBalance());
        assertTrue(result.walletActive());
    }

    @Test
    @DisplayName("getUserDetail: should throw UserNotFoundException when user does not exist")
    void getUserDetail_UserNotFound() {
        when(userRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> adminUserService.getUserDetail(publicId));
        verify(walletRepository, never()).findByOwnerEmail(anyString());
    }

    @Test
    @DisplayName("toggleUserStatus: should toggle user and wallet isActive flag")
    void toggleUserStatus_Success() {
        when(userRepository.findByPublicId(publicId)).thenReturn(Optional.of(user));
        when(walletRepository.findByOwnerEmail(user.getEmail())).thenReturn(Optional.of(wallet));

        UserAdminDto result = adminUserService.toggleUserStatus(publicId, false);

        assertNotNull(result);
        assertFalse(result.isActive());
        assertFalse(user.getIsActive());
        assertFalse(wallet.getIsActive());

        verify(userRepository).save(user);
        verify(walletRepository).save(wallet);
    }
}
