package com.byarrival.domain.admin.dto.request;

import com.byarrival.domain.admin.entity.AdminArticleTab;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 기사 목록 검색 조건(ARTICLE-01·02). 값을 보내지 않은 조건은 적용하지 않는다.
 *
 * @param tab           기본 ALL
 * @param page          1부터 시작. 기본 1
 * @param keyword       제목 검색어
 * @param collectedFrom 수집일 시작(포함, yyyy-MM-dd)
 * @param collectedTo   수집일 끝(포함, yyyy-MM-dd)
 */
public record AdminArticleSearchRequest(
    AdminArticleTab tab,
    @Min(1) Integer page,
    @Size(max = 100) String keyword,
    @Size(max = 50) String pressName,
    Long categoryId,
    ArticleDifficulty difficulty,
    ArticleProcessStatus processStatus,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate collectedFrom,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate collectedTo
) {

    public AdminArticleSearchRequest {
        tab = tab == null ? AdminArticleTab.ALL : tab;
        page = page == null ? Integer.valueOf(1) : page;
    }
}
