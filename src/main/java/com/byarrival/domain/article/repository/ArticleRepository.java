package com.byarrival.domain.article.repository;

import com.byarrival.domain.article.entity.Article;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query("SELECT a.guid FROM Article a WHERE a.guid IN :guids")
    List<String> findExistingGuids(@Param("guids") Collection<String> guids);

    @Query("SELECT a.originalUrl FROM Article a WHERE a.originalUrl IN :originalUrls")
    List<String> findExistingOriginalUrls(@Param("originalUrls") Collection<String> originalUrls);
}
