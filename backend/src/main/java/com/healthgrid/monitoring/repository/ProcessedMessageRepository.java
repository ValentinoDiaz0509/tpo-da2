package com.healthgrid.monitoring.repository;

import com.healthgrid.monitoring.model.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM ProcessedMessage p WHERE p.messageFingerprint = :fingerprint " +
           "AND p.processedAt > :cutoff")
    boolean isRecentlyProcessed(@Param("fingerprint") String fingerprint,
                                @Param("cutoff") LocalDateTime cutoff);
}
