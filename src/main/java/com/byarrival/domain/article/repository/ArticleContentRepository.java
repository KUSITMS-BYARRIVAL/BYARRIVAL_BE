package com.byarrival.domain.article.repository;

import com.byarrival.domain.article.entity.ArticleContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleContentRepository extends JpaRepository<ArticleContent, Long> {
}
