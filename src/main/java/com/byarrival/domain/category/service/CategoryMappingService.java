package com.byarrival.domain.category.service;

import com.byarrival.domain.category.entity.CategoryMapping;
import com.byarrival.domain.category.repository.CategoryMappingRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 언론사 카테고리를 서비스 카테고리로 바꾸는 매핑 규칙을 조회한다.
 */
@Service
@RequiredArgsConstructor
public class CategoryMappingService {

    private final CategoryMappingRepository categoryMappingRepository;

    /**
     * @return 언론사 카테고리 → 서비스 카테고리 ID
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCategoryIds(Long feedId) {
        return categoryMappingRepository.findAllByFeedId(feedId).stream()
            .collect(Collectors.toMap(CategoryMapping::getRssCategory, CategoryMapping::getCategoryId));
    }

    /**
     * @return 매핑 규칙이 하나라도 있는 서비스 카테고리 ID. 배치가 기사를 채워야 하는 카테고리다.
     */
    @Transactional(readOnly = true)
    public Set<Long> getMappedCategoryIds() {
        return categoryMappingRepository.findAll().stream()
            .map(CategoryMapping::getCategoryId)
            .collect(Collectors.toUnmodifiableSet());
    }
}
