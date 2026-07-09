package com.mlbfantasy.dto;

import com.mlbfantasy.model.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName, String role, String avatarUrl) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getAvatarUrl());
    }
}
