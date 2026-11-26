package com.byarrival.domain.smalltalk.repository;

import com.byarrival.domain.smalltalk.entity.Smalltalk;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SmalltalkRepository extends JpaRepository<Smalltalk, Long> {

    // 운영자가 숨겼거나(visibility = HIDDEN) 삭제한 스몰토크는 뺀다
    @Query("SELECT s FROM Smalltalk s WHERE s.serviceDate = :serviceDate AND s.visibility = com.byarrival.domain.smalltalk.entity.SmalltalkVisibility.VISIBLE "
        + "AND s.deletedAt IS NULL ORDER BY s.id")
    List<Smalltalk> findVisibleByServiceDate(@Param("serviceDate") LocalDate serviceDate);

    @Query("SELECT s.id FROM Smalltalk s WHERE s.id IN :ids AND s.visibility = com.byarrival.domain.smalltalk.entity.SmalltalkVisibility.VISIBLE AND s.deletedAt IS NULL")
    List<Long> findVisibleIds(@Param("ids") Collection<Long> ids);

    // 배치가 같은 기사로 다시 만들지 않고 삭제한 자리를 다시 채우지 않도록 삭제한 것까지 돌려준다
    @Query("SELECT s.articleId FROM Smalltalk s WHERE s.serviceDate = :serviceDate")
    List<Long> findArticleIdsByServiceDate(@Param("serviceDate") LocalDate serviceDate);

    // 어드민 에디터 픽 목록. 삭제한 것만 뺀다
    @Query("SELECT s FROM Smalltalk s WHERE s.serviceDate = :serviceDate AND s.deletedAt IS NULL ORDER BY s.id")
    List<Smalltalk> findNotDeletedByServiceDate(@Param("serviceDate") LocalDate serviceDate);

    boolean existsByArticleId(Long articleId);
}
