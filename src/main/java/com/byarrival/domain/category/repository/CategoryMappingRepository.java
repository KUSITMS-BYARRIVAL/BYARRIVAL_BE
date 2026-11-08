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
}
