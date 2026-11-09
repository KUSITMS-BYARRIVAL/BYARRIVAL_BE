package com.byarrival.domain.category.service;

import com.byarrival.domain.category.entity.CategoryMapping;
import com.byarrival.domain.category.repository.CategoryMappingRepository;
import com.byarrival.domain.category.repository.CategoryMappingRepository.CategoryName;
import java.util.LinkedHashMap;
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
     * @return 활성 서비스 카테고리 ID. 사용자가 온보딩에서 고를 수 있으므로 배치가 매일 기사를 채워야 하는 카테고리다.
     *         매핑 규칙이 빠진 카테고리도 포함해, 매핑 누락이 0건으로 드러나게 한다.
     */
    @Transactional(readOnly = true)
    public Set<Long> getActiveCategoryIds() {
        return Set.copyOf(categoryMappingRepository.findActiveCategoryIds());
    }

    /**
     * @return 활성 서비스 카테고리 ID → 이름. 화면 순서(display_order)대로 담는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getActiveCategoryNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (CategoryName category : categoryMappingRepository.findActiveCategoryNames()) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }
}
