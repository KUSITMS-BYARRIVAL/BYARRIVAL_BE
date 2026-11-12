package com.byarrival.domain.smalltalk.repository;

import com.byarrival.domain.smalltalk.entity.Smalltalk;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SmalltalkRepository extends JpaRepository<Smalltalk, Long> {

    // 관리자가 숨긴 스몰토크(visibility = HIDDEN)는 뺀다
    @Query(value = "SELECT * FROM smalltalk WHERE service_date = :serviceDate AND visibility = 'VISIBLE' ORDER BY id",
        nativeQuery = true)
    List<Smalltalk> findVisibleByServiceDate(@Param("serviceDate") LocalDate serviceDate);

    @Query(value = "SELECT id FROM smalltalk WHERE id IN (:ids) AND visibility = 'VISIBLE'", nativeQuery = true)
    List<Long> findVisibleIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT s.articleId FROM Smalltalk s WHERE s.serviceDate = :serviceDate")
    List<Long> findArticleIdsByServiceDate(@Param("serviceDate") LocalDate serviceDate);
}
