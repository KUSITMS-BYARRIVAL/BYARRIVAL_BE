package com.byarrival.domain.scrap.repository;

import com.byarrival.domain.scrap.entity.Scrap;
import com.byarrival.domain.scrap.entity.ScrapTargetType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    Optional<Scrap> findByUserIdAndTargetTypeAndTargetId(Long userId, ScrapTargetType targetType, Long targetId);

    Optional<Scrap> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT s FROM Scrap s WHERE s.userId = :userId AND s.targetType = :targetType AND s.deletedAt IS NULL")
    List<Scrap> findActive(@Param("userId") Long userId, @Param("targetType") ScrapTargetType targetType);

    @Query("SELECT s.targetId FROM Scrap s WHERE s.userId = :userId AND s.targetType = :targetType "
        + "AND s.targetId IN :targetIds AND s.deletedAt IS NULL")
    List<Long> findActiveTargetIds(@Param("userId") Long userId, @Param("targetType") ScrapTargetType targetType,
        @Param("targetIds") Collection<Long> targetIds);

    @Query("SELECT s.targetType AS targetType, COUNT(s) AS scrapCount FROM Scrap s "
        + "WHERE s.userId = :userId AND s.deletedAt IS NULL GROUP BY s.targetType")
    List<TypeCount> countActiveByType(@Param("userId") Long userId);

    interface TypeCount {

        ScrapTargetType getTargetType();

        long getScrapCount();
    }
}
