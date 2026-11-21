package com.byarrival.domain.article.service;

import com.byarrival.domain.ai.service.ClaudeClient.ClaudeRequest;
import com.byarrival.domain.ai.service.ClaudeClient.ClaudeResult;
import com.byarrival.domain.ai.service.ClaudeClient;
import com.byarrival.domain.article.config.ArticleSummaryProperties;
import com.byarrival.domain.article.dto.ArticleSummaryOutput;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * AI-06 프롬프트로 기사 1건의 3문장 요약을 생성한다(AI-01).
 */
@Component
public class ArticleSummaryGenerator {

    static final String SYSTEM_PROMPT_PATH = "prompts/article-summary-system.txt";

    private final ClaudeClient claudeClient;
    private final ArticleSummaryProperties properties;
    private final String systemPrompt;

    public ArticleSummaryGenerator(ClaudeClient claudeClient, ArticleSummaryProperties properties) {
        this.claudeClient = claudeClient;
        this.properties = properties;
        this.systemPrompt = loadSystemPrompt();
    }

    public ClaudeResult<ArticleSummaryOutput> generate(String title, String content, ArticleDifficulty difficulty) {
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputEffort(), systemPrompt, userMessage(title, content, difficulty));
        return claudeClient.generate(request, ArticleSummaryOutput.class);
    }

    /**
     * 검증에 실패한 이전 응답과 어긴 규칙을 함께 보내 다시 쓰게 한다.
     */
    public ClaudeResult<ArticleSummaryOutput> regenerate(String title, String content, ArticleDifficulty difficulty,
        ArticleSummaryOutput previous, String problems) {
        String message = userMessage(title, content, difficulty) + "\n\n" + feedback(previous, problems);
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputRegenerateEffort(), systemPrompt, message);
        return claudeClient.generate(request, ArticleSummaryOutput.class);
    }

    /**
     * 형식만 어긴 이전 답을 기사 본문 없이 고치게 한다. 본문을 다시 보내지 않고 낮은 생각 깊이로 호출해 재요청 비용을 줄인다.
     * 근거 구절은 이미 본문과 대조해 통과했으므로 그대로 두게 하고, 서버는 고친 답을 본문과 다시 대조한다.
     */
    public ClaudeResult<ArticleSummaryOutput> repair(String title, ArticleDifficulty difficulty,
        ArticleSummaryOutput previous, String problems) {
        ClaudeRequest request = new ClaudeRequest(properties.model(), properties.maxTokens(),
            properties.outputRepairEffort(), systemPrompt, repairMessage(title, difficulty, previous, problems));
        return claudeClient.generate(request, ArticleSummaryOutput.class);
    }

    static String repairMessage(String title, ArticleDifficulty difficulty, ArticleSummaryOutput previous,
        String problems) {
        return "<title>" + title + "</title>\n"
            + "<difficulty>" + difficulty.name() + "</difficulty>\n"
            + "<previous_answer>\n"
            + "oneLine: " + previous.oneLine() + "\n"
            + numbered("summary", previous.summary())
            + numbered("evidence", previous.evidence())
            + "keywords: " + previous.keywords() + "\n"
            + "</previous_answer>\n"
            + "<problems>\n" + problems + "\n</problems>\n"
            + "이번에는 기사 본문 없이 이전 답만 고칩니다. 위 문제만 고치고 나머지는 그대로 둡니다.\n"
            + "- title은 <title>의 제목을 글자 그대로 씁니다.\n"
            + "- 글자 수를 줄일 때는 수식어나 부가 정보를 덜어 냅니다. 이전 답에 없던 내용이나 숫자를 새로 넣지 않습니다.\n"
            + "- evidence는 이전 답의 구절을 글자 하나 바꾸지 말고 같은 순서로 그대로 옮깁니다.";
    }

    // 쉼표가 들어간 문장도 구분되도록 한 줄에 하나씩 번호를 붙인다
    private static String numbered(String name, List<String> values) {
        StringBuilder text = new StringBuilder(name).append(":\n");
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                text.append(i + 1).append(". ").append(values.get(i)).append("\n");
            }
        }
        return text.toString();
    }

    static String feedback(ArticleSummaryOutput previous, String problems) {
        return "<previous_answer>\n"
            + "oneLine: " + previous.oneLine() + "\n"
            + "summary: " + previous.summary() + "\n"
            + "evidence: " + previous.evidence() + "\n"
            + "keywords: " + previous.keywords() + "\n"
            + "</previous_answer>\n"
            + "<problems>\n" + problems + "\n</problems>\n"
            + "이전 답이 위 규칙을 어겼습니다. 문제를 모두 고쳐 처음부터 다시 쓰세요. 규칙을 지킨 부분은 그대로 둬도 됩니다.";
    }

    static String userMessage(String title, String content, ArticleDifficulty difficulty) {
        return "<title>" + title + "</title>\n"
            + "<difficulty>" + difficulty.name() + "</difficulty>\n"
            + "<article>\n" + content + "\n</article>";
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource(SYSTEM_PROMPT_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("요약 프롬프트를 읽지 못했습니다: " + SYSTEM_PROMPT_PATH, e);
        }
    }
}
