package com.footverse.review.dto;

import java.time.LocalDateTime;

/**
 * A single product review returned to any reader (dto-spec §16). The author is exposed only through
 * display fields ({@code userFullName}, {@code userAvatarUrl}); the underlying {@code User} entity
 * is never serialized. The client shows an "edited" indicator when {@code updatedAt} differs from
 * {@code createdAt}.
 *
 * @param id            the review id
 * @param userFullName  the author's full name
 * @param userAvatarUrl the author's avatar URL, if any
 * @param rating        the rating, 1–5
 * @param comment       the optional comment (max 500 chars), if any
 * @param createdAt     the creation timestamp
 * @param updatedAt     the last-update timestamp
 */
public record ReviewResponse(
        Long id,
        String userFullName,
        String userAvatarUrl,
        int rating,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
