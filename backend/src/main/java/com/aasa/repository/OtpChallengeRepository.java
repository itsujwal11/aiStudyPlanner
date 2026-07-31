package com.aasa.repository;

import com.aasa.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    Optional<OtpChallenge> findTopByUserIdAndPurposeOrderByCreatedAtDesc(
            Long userId,
            OtpChallenge.Purpose purpose
    );

    List<OtpChallenge> findByUserIdAndPurposeAndConsumedAtIsNull(
            Long userId,
            OtpChallenge.Purpose purpose
    );
}
