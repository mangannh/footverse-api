package com.footverse.review.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.footverse.review.dto.ReviewResponse;
import com.footverse.review.entity.Review;

/**
 * Maps {@link Review} entities to their response DTO. Pure single-entity mapping only — no
 * aggregation and no cross-service or repository calls (architecture-spec §9). The author's display
 * fields are read from the review's own {@code user} association, a plain same-aggregate reference
 * walk (the {@code couponCode} precedent), not an aggregation.
 */
@Mapper
public interface ReviewMapper {

    /**
     * Maps a review to its response representation. The author's {@code userFullName} and
     * {@code userAvatarUrl} are taken from the review's {@code user} reference; every other field —
     * {@code id}, {@code rating}, {@code comment}, and the inherited {@code createdAt} /
     * {@code updatedAt} audit timestamps — maps by name.
     *
     * @param review the review entity
     * @return the response DTO
     */
    @Mapping(target = "userFullName", source = "user.fullName")
    @Mapping(target = "userAvatarUrl", source = "user.avatarUrl")
    ReviewResponse toResponse(Review review);
}
