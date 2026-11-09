package com.byarrival.domain.category.repository;

import com.byarrival.domain.category.entity.CategoryMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryMappingRepository extends JpaRepository<CategoryMapping, Long> {

    List<CategoryMapping> findAllByFeedId(Long feedId);

    // 사용자가 고를 수 있는 서비스 카테고리. category 엔티티가 아직 없어 ID만 조회한다.
    @Query(value = "SELECT id FROM category WHERE active", nativeQuery = true)
    List<Long> findActiveCategoryIds();

    // 알림에 카테고리 이름을 보여 주기 위한 조회. 화면 순서(display_order)대로 돌려준다.
    @Query(value = "SELECT id, name FROM category WHERE active ORDER BY display_order, id", nativeQuery = true)
    List<CategoryName> findActiveCategoryNames();

    interface CategoryName {

        Long getId();

        String getName();
    }
}
