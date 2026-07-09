package com.mlbfantasy.dto;

import jakarta.validation.constraints.NotBlank;

/** avatarDataUrl is a client-resized image encoded as a data: URL (e.g. "data:image/jpeg;base64,..."). */
public record UpdateAvatarRequest(@NotBlank String avatarDataUrl) {
}
