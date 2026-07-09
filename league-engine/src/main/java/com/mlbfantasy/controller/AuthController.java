package com.mlbfantasy.controller;

import com.mlbfantasy.dto.AuthResponse;
import com.mlbfantasy.dto.LoginRequest;
import com.mlbfantasy.dto.RegisterRequest;
import com.mlbfantasy.dto.UpdateAvatarRequest;
import com.mlbfantasy.dto.UserResponse;
import com.mlbfantasy.security.AppUserPrincipal;
import com.mlbfantasy.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(new UserResponse(
                principal.getId(),
                principal.getEmail(),
                principal.getDisplayName(),
                principal.getRole(),
                principal.getAvatarUrl()));
    }

    @PatchMapping("/me/avatar")
    public ResponseEntity<UserResponse> updateAvatar(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UpdateAvatarRequest request) {
        return ResponseEntity.ok(authService.updateAvatar(principal.getId(), request.avatarDataUrl()));
    }
}
