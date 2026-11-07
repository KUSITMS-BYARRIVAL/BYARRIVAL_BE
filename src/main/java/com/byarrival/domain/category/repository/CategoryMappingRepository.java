package com.byarrival.domain.category.repository;

import com.byarrival.domain.category.entity.CategoryMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryMappingRepository extends JpaRepository<CategoryMapping, Long> {

    List<CategoryMapping> findAllByFeedId(Long feedId);
}
