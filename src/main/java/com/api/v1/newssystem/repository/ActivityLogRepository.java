package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.ActivityLog;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Deliberately not a JpaRepository: the log can only be added to and read. The one delete is the retention job's.
 */
@org.springframework.stereotype.Repository
public interface ActivityLogRepository extends Repository<ActivityLog, Long> {
    <S extends ActivityLog> S save(S entry);

    Optional<ActivityLog> findById(Long id);

    long count();

    // every filter is optional (null means "any"); `search` is a ready LIKE pattern: lower case, with % _ and ! escaped
    // by "!". When allActions is true `actions` is ignored (it still needs one value, a list cannot be empty here).
    @Query("""
            select a from ActivityLog a
            where (:actorId is null or a.actor.id = :actorId)
              and (:allActions = true or a.action in :actions)
              and (:targetType is null or a.targetType = :targetType)
              and (:targetId is null or a.targetId = :targetId)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt < :to)
              and (:search is null
                   or lower(a.targetLabel) like :search escape '!'
                   or lower(a.actorName) like :search escape '!')
            """)
    Page<ActivityLog> search(@Param("actorId") Long actorId,
                             @Param("allActions") boolean allActions,
                             @Param("actions") Collection<ActivityAction> actions,
                             @Param("targetType") ActivityTargetType targetType,
                             @Param("targetId") Long targetId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("search") String search,
                             Pageable pageable);

    // the newest entries of some actions (the content feed of the dashboard), in the order of `pageable`
    List<ActivityLog> findAllByActionIn(Collection<ActivityAction> actions, Pageable pageable);

    // the retention job only
    @Modifying
    @Query("delete from ActivityLog a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
