package com.byarrival.domain.quiz.service;

import com.byarrival.domain.quiz.entity.Quiz;
import com.byarrival.domain.quiz.entity.QuizChoice;
import com.byarrival.domain.quiz.repository.QuizRepository;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 퀴즈 생성 입력을 읽고 결과를 저장한다. AI 호출이 트랜잭션 밖에서 일어나도록 읽기와 쓰기를 따로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class QuizStore {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final QuizRepository quizRepository;

    @Transactional(readOnly = true)
    public Set<Long> findArticleIdsWithQuiz(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(quizRepository.findArticleIdsWithQuiz(articleIds));
    }

    /**
     * 퀴즈를 저장한다. 그 사이 같은 기사의 퀴즈가 저장됐으면(배치 재실행이 겹친 경우) uk_quiz_article 위반으로
     * DataIntegrityViolationException이 난다. 트랜잭션 밖의 호출부에서 중복으로 처리한다.
     */
    @Transactional
    public void save(Long articleId, ValidatedQuiz quiz, LocalDateTime now) {
        List<QuizChoice> choices = IntStream.range(0, quiz.choices().size())
            .mapToObj(i -> new QuizChoice(i + 1, quiz.choices().get(i)))
            .toList();
        quizRepository.saveAndFlush(Quiz.create(articleId, quiz.question(), JSON_MAPPER.writeValueAsString(choices),
            quiz.answerSeq(), quiz.explanation(), now));
    }

    /**
     * 재생성한 퀴즈를 새 버전으로 저장한다(ARTICLE-06). 기존 ACTIVE 퀴즈는 지우지 않고 INACTIVE로 내린다.
     * 기사당 ACTIVE 퀴즈는 하나뿐이어야 하므로 내린 것을 먼저 반영한 뒤 새 퀴즈를 넣는다.
     */
    @Transactional
    public void replace(Long articleId, ValidatedQuiz quiz, LocalDateTime now) {
        quizRepository.findActiveByArticleIds(List.of(articleId)).forEach(Quiz::deactivate);
        quizRepository.flush();
        save(articleId, quiz, now);
    }

    static List<QuizChoice> choicesOf(Quiz quiz) {
        return List.of(JSON_MAPPER.readValue(quiz.getChoices(), QuizChoice[].class));
    }
}
