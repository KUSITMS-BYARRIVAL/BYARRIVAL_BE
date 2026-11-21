package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.anthropic.models.messages.OutputConfig;
import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.article.config.ArticleSummaryProperties;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArticleSummaryGeneratorTest {

    @Test
    void 수선_요청에는_기사_본문_없이_제목_난이도_이전_답_어긴_규칙만_담는다() {
        ArticleSummaryOutput previous = new ArticleSummaryOutput("한은, 기준금리 동결", ArticleDifficulty.HARD,
            "한국은행이 기준금리를 동결했습니다.", List.of("첫째, 문장입니다.", "둘째 문장입니다.", "셋째 문장입니다."),
            List.of("한국은행이 1일, 기준금리를 동결했다", "물가가 둔화됐다", "연내 인하를 점친다"), List.of("기준금리", "한국은행", "물가"));

        String message = ArticleSummaryGenerator.repairMessage("한은, 기준금리 동결", ArticleDifficulty.HARD, previous,
            "summary 1번 문장이 31자입니다.");

        assertThat(message)
            .contains("<title>한은, 기준금리 동결</title>", "<difficulty>HARD</difficulty>",
                "<problems>\nsummary 1번 문장이 31자입니다.\n</problems>")
            // 쉼표가 든 문장도 구분되도록 한 줄에 하나씩 번호를 붙인다
            .contains("summary:\n1. 첫째, 문장입니다.\n2. 둘째 문장입니다.\n3. 셋째 문장입니다.\n")
            .contains("evidence:\n1. 한국은행이 1일, 기준금리를 동결했다\n2. 물가가 둔화됐다\n3. 연내 인하를 점친다\n")
            .doesNotContain("<article>");
    }

    @Test
    void 첫_시도_본문과_함께_다시_쓰기_수선은_각각_설정한_생각_깊이로_호출한다() {
        ClaudeClient claudeClient = mock(ClaudeClient.class);
        ArticleSummaryGenerator generator = new ArticleSummaryGenerator(claudeClient,
            new ArticleSummaryProperties("claude-haiku-5-5", 4000, "low", "medium", "low", 8, 3));
        ArticleSummaryOutput previous = new ArticleSummaryOutput("제목", ArticleDifficulty.EASY, "한 줄 요약입니다.",
            List.of("첫째.", "둘째.", "셋째."), List.of("근거 하나", "근거 둘", "근거 셋"), List.of("가", "나", "다"));

        generator.generate("제목", "본문", ArticleDifficulty.EASY);
        generator.regenerate("제목", "본문", ArticleDifficulty.EASY, previous, "문제");
        generator.repair("제목", ArticleDifficulty.EASY, previous, "문제");

        ArgumentCaptor<ClaudeRequest> requests = ArgumentCaptor.forClass(ClaudeRequest.class);
        verify(claudeClient, times(3)).generate(requests.capture(), eq(ArticleSummaryOutput.class));
        assertThat(requests.getAllValues()).extracting(ClaudeRequest::effort)
            .containsExactly(OutputConfig.Effort.LOW, OutputConfig.Effort.MEDIUM, OutputConfig.Effort.LOW);
    }
}
