package com.byarrival.domain.term.repository;

import com.byarrival.domain.term.entity.Terms;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    /**
     * 종류별 최신 버전 약관. 개정 시 새 행을 추가하므로 ID가 가장 큰 행을 최신으로 본다.
     */
    @Query("SELECT t FROM Terms t WHERE t.id IN (SELECT MAX(t2.id) FROM Terms t2 GROUP BY t2.termsType) "
        + "ORDER BY t.id")
    List<Terms> findLatestOfEachType();
}
