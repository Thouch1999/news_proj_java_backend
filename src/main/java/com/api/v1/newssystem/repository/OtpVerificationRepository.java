package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.OtpVerification;
import com.api.v1.newssystem.model.enumerate.OtpPurpose;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);
    List<OtpVerification> findAllByEmailAndPurposeAndConsumedAtIsNull(String email, OtpPurpose purpose);
    long countByEmailAndCreatedAtAfter(String email, LocalDateTime since);
}
