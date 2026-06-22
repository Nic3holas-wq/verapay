package com.nicko.verapay.admin.controller;

import com.nicko.verapay.admin.service.AdminTransactionReversalService;
import com.nicko.verapay.admin.service.AdminTransactionService;
import com.nicko.verapay.admin.service.AdminUserService;
import com.nicko.verapay.dto.TransactionReversalRequestDto;
import com.nicko.verapay.dto.admin.*;
import com.nicko.verapay.dto.transactions.TransactionResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminTransactionService adminTransactionService;
    private final AdminTransactionReversalService adminTransactionReversalService;

    // GET /api/admin/users?email=&isActive=&page=0&size=20
    @GetMapping(value = "/users", version = "1.0")
    public ResponseEntity<Page<UserAdminDto>> getAllUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                adminUserService.getAllUsers(email, isActive, page, size));
    }

    // GET /api/admin/users/{publicId}
    @GetMapping(value = "/users/{publicId}", version = "1.0")
    public ResponseEntity<UserDetailAdminDto> getUserDetail(
            @PathVariable UUID publicId) {
        return ResponseEntity.ok(adminUserService.getUserDetail(publicId));
    }

    // PATCH /api/admin/users/{publicId}/status
    @PatchMapping(value = "/users/{publicId}/status", version = "1.0")
    public ResponseEntity<UserAdminDto> toggleUserStatus(
            @PathVariable UUID publicId,
            @RequestBody @Valid ToggleUserStatusDto request) {
        return ResponseEntity.ok(
                adminUserService.toggleUserStatus(publicId, request.isActive()));
    }

    // GET /api/admin/transactions?status=&page=0&size=20
    @GetMapping(value = "/transactions", version = "1.0")
    public ResponseEntity<Page<AdminTransactionDto>> getAllTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                adminTransactionService.getAllTransactions(status, page, size));
    }

    // GET /api/admin/transactions/stats
    @GetMapping(value = "/transactions/stats", version = "1.0")
    public ResponseEntity<TransactionStatsDto> getTransactionStats() {
        return ResponseEntity.ok(adminTransactionService.getStats());
    }

    // PATCH /api/admin/transactions/{id}/reverse
    @PatchMapping(value = "/transactions/{id}/reverse", version = "1.0")
    public ResponseEntity<TransactionResponseDto> reverseTransaction(
            @PathVariable Long id,
            @RequestBody @Valid TransactionReversalRequestDto request) {
        return ResponseEntity.ok(
                adminTransactionReversalService.reverseTransaction(id, request.reason()));
    }
}
