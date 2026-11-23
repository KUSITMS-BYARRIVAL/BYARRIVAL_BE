package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.entity.ArticleVisibilityHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleVisibilityHistoryRepository extends JpaRepository<ArticleVisibilityHistory, Long> {
}
