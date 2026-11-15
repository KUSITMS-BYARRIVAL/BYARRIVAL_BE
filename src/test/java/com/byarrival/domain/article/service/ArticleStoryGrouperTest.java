package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient.TokenUsage;
import com.byarrival.domain.article.config.ArticleStoryProperties;
import com.byarrival.domain.article.dto.ArticleStoryOutput;
import com.byarrival.domain.article.dto.ArticleStoryOutput.StoryGroup;
import com.byarrival.domain.article.service.ArticleStoryGrouper.GroupResult;
import com.byarrival.domain.article.service.ArticleStoryGrouper.StoryGroupingException;
import com.byarrival.domain.article.service.ArticleStoryGrouper.StoryInput;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArticleStoryGrouperTest {

    private static final List<StoryInput> ARTICLES = List.of(
        new StoryInput(1L, "경제·금융", "금융보안원도 털렸다…정보유출", "금융보안원이 해킹당했습니다."),
        new StoryInput(2L, "IT·기술", "[단독] 금융보안원 상시평가시스템 해킹 정황", "금융보안원이 유출 기업에 연락 중입니다."),
        new StoryInput(3L, "스포츠", "삼성 우승 신문 수집 열풍", "팬들이 우승 신문을 모읍니다."),
        new StoryInput(4L, "스포츠", "kt 선수들 팬들에게 사과", "kt 선수들이 사과했습니다."));

    private final ClaudeClient claudeClient = mock(ClaudeClient.class);
    private final ArticleStoryGrouper grouper = new ArticleStoryGrouper(claudeClient,
        new ArticleStoryProperties("claude-haiku-5-5", 16000, "medium", 2));

    @Test
    void 기사_전체를_한_번에_보내_같은_사건_묶음을_받는다() {
        given(claudeClient.generate(any(), eq(ArticleStoryOutput.class)))
            .willReturn(result(new StoryGroup("금융보안원 해킹", List.of(1L, 2L))));

        GroupResult result = grouper.group(ARTICLES);

        assertThat(result.groups()).containsExactly(Set.of(1L, 2L));
        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.usage()).isEqualTo(new TokenUsage(15000, 2500));
    }

    @Test
    void 기사마다_번호_카테고리_제목_한_줄_요약을_한_줄로_보낸다() {
        given(claudeClient.generate(any(), eq(ArticleStoryOutput.class))).willReturn(result());
        List<StoryInput> articles = List.of(
            new StoryInput(7L, "정치·사회", "제목에\n줄바꿈", "요약  공백"),
            new StoryInput(8L, "스포츠", "다른 기사", null));

        grouper.group(articles);

        ArgumentCaptor<ClaudeRequest> request = ArgumentCaptor.forClass(ClaudeRequest.class);
        verify(claudeClient).generate(request.capture(), eq(ArticleStoryOutput.class));
        assertThat(request.getValue().userMessage())
            .contains("7 | 정치·사회 | 제목에 줄바꿈 | 요약 공백")
            .contains("8 | 스포츠 | 다른 기사 | ");
        assertThat(request.getValue().systemPrompt()).contains("같은 사건");
    }

    @Test
    void 한_건뿐인_묶음은_버리고_같은_번호가_한_묶음에_두_번_있으면_하나로_친다() {
        given(claudeClient.generate(any(), eq(ArticleStoryOutput.class)))
            .willReturn(result(new StoryGroup("혼자", List.of(3L)), new StoryGroup("해킹", List.of(1L, 2L, 2L))));

        GroupResult result = grouper.group(ARTICLES);

        assertThat(result.groups()).containsExactly(Set.of(1L, 2L));
    }

    @Test
    void 같은_사건이_없으면_빈_묶음을_돌려준다() {
        given(claudeClient.generate(any(), eq(ArticleStoryOutput.class))).willReturn(result());

        assertThat(grouper.group(ARTICLES).groups()).isEmpty();
    }

    @Test
    void 없는_번호나_두_묶음에_든_기사가_있으면_다시_요청한다() {
        given(claudeClient.generate(any(), eq(ArticleStoryOutput.class)))
            .willReturn(result(new StoryGroup("해킹", List.of(1L, 99L))))
            .willReturn(result(new StoryGroup("해킹", List.of(1L, 2L)), new StoryGroup("야구", List.of(2L, 4L))))
            .willReturn(result(new StoryGroup("해킹", List.of(1L, 2L))));

        GroupResult result = grouper.group(ARTICLES);

        assertThat(result.groups()).containsExactly(Set.of(1L, 2L));
        assertThat(result.calls()).isEqualTo(3);
        assertThat(result.usage()).isEqualTo(new TokenUsage(45000, 7500));
    }

    @Test
    void 다시_요청해도_통과하지_못하면_쓴_토큰과_함께_실패한다() {
        given(claudeClient.generate(any(), eq(ArticleStoryOutput.class)))
            .willReturn(new ClaudeResult<>(new ArticleStoryOutput(null), new TokenUsage(15000, 10)));

        assertThatThrownBy(() -> grouper.group(ARTICLES))
            .isInstanceOfSatisfying(StoryGroupingException.class, e -> {
                assertThat(e.calls()).isEqualTo(3);
                assertThat(e.usage()).isEqualTo(new TokenUsage(45000, 30));
            });
    }

    private ClaudeResult<ArticleStoryOutput> result(StoryGroup... groups) {
        return new ClaudeResult<>(new ArticleStoryOutput(List.of(groups)), new TokenUsage(15000, 2500));
    }
}
