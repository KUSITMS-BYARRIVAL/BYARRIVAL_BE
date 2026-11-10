package com.byarrival.domain.article.controller;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.service.ArticleAiService;
import com.byarrival.domain.article.service.ArticleAiService.AiProcessResult;
import com.byarrival.domain.article.service.ArticleSummaryGenerator;
import com.byarrival.domain.article.service.ArticleSummaryStore.SummaryInput;
import com.byarrival.domain.article.service.ArticleSummaryStore;
import com.byarrival.domain.batch.service.DailyBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬에서 수집 배치와 AI 요약을 직접 실행해 보는 테스트 API (local 프로필 전용)
 */
@Tag(name = "Test", description = "로컬 확인용 테스트 API")
@Profile("local")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class ArticleSummaryTestController {

    private final DailyBatchService dailyBatchService;
    private final ArticleSummaryStore articleSummaryStore;
    private final ArticleAiService articleAiService;
    private final ArticleSummaryGenerator articleSummaryGenerator;

    @Operation(summary = "일일 수집 배치 실행", description = "23:45 배치와 같은 수집을 지금 실행한다. 서비스일은 내일이다.")
    @PostMapping("/batch")
    public ApiResponse<Void> runBatch() {
        dailyBatchService.run();
        return ApiResponse.ok();
    }

    @Operation(summary = "AI 가공 실행",
        description = "서비스일의 정제된 기사를 난이도 판정 후 요약한다. 배치의 AI 단계와 같다. 실제 Claude API를 호출하므로 비용이 든다.")
    @PostMapping("/ai")
    public ApiResponse<AiProcessResult> processAi(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        return ApiResponse.ok(articleAiService.process(serviceDate));
    }

    @Operation(summary = "AI 요약 미리보기",
        description = "기사 1건의 요약 응답을 검증·저장 없이 그대로 돌려준다. 프롬프트를 고칠 때 쓴다. 실제 API를 호출한다.")
    @PostMapping("/summaries/preview")
    public ApiResponse<SummaryPreview> preview(@RequestParam Long articleId,
        @RequestParam(defaultValue = "HARD") ArticleDifficulty difficulty) {
        SummaryInput input = articleSummaryStore.loadInputs(List.of(articleId)).stream().findFirst()
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        ArticleSummaryOutput output = articleSummaryGenerator.generate(input.title(), input.content(), difficulty)
            .output();
        return ApiResponse.ok(SummaryPreview.of(input.title(), output));
    }

    public record SummaryPreview(String title, ArticleSummaryOutput output, int oneLineLength,
        List<Integer> sentenceLengths, List<Integer> keywordLengths) {

        static SummaryPreview of(String title, ArticleSummaryOutput output) {
            return new SummaryPreview(title, output, length(output.oneLine()),
                lengths(output.summary()), lengths(output.keywords()));
        }

        private static List<Integer> lengths(List<String> texts) {
            return texts == null ? List.of() : texts.stream().map(SummaryPreview::length).toList();
        }

        private static int length(String text) {
            return text == null ? 0 : text.strip().codePointCount(0, text.strip().length());
        }
    }
}
