package com.mlbfantasy.service;

import com.mlbfantasy.dto.AuthResponse;
import com.mlbfantasy.dto.LoginRequest;
import com.mlbfantasy.dto.RegisterRequest;
import com.mlbfantasy.dto.UserResponse;
import com.mlbfantasy.exception.ApiException;
import com.mlbfantasy.model.User;
import com.mlbfantasy.repository.UserRepository;
import com.mlbfantasy.security.JwtService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        if (userRepository.existsByDisplayName(request.displayName())) {
            throw ApiException.conflict("Display name already taken");
        }
        User user = new User(email, request.displayName(), passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    @Transactional
    public UserResponse updateAvatar(UUID userId, String avatarDataUrl) {
        if (!avatarDataUrl.startsWith("data:image/")) {
            throw ApiException.badRequest("avatarDataUrl must be an image data URL");
        }
        if (avatarDataUrl.length() > MAX_AVATAR_DATA_URL_LENGTH) {
            throw ApiException.badRequest("Avatar image is too large");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setAvatarUrl(avatarDataUrl);
        return UserResponse.from(userRepository.save(user));
    }

    // ~300KB of base64, comfortably fits a small client-resized square JPEG/PNG.
    private static final int MAX_AVATAR_DATA_URL_LENGTH = 400_000;
}
