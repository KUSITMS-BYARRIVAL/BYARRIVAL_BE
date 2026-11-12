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
import org.springframework.dao.DataIntegrityViolationException;
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
     * @return 저장했으면 true. 그 사이 같은 기사의 퀴즈가 저장됐으면(배치 재실행) false
     */
    @Transactional
    public boolean save(Long articleId, ValidatedQuiz quiz, LocalDateTime now) {
        List<QuizChoice> choices = IntStream.range(0, quiz.choices().size())
            .mapToObj(i -> new QuizChoice(i + 1, quiz.choices().get(i)))
            .toList();
        try {
            quizRepository.saveAndFlush(Quiz.create(articleId, quiz.question(), JSON_MAPPER.writeValueAsString(choices),
                quiz.answerSeq(), quiz.explanation(), now));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    static List<QuizChoice> choicesOf(Quiz quiz) {
        return List.of(JSON_MAPPER.readValue(quiz.getChoices(), QuizChoice[].class));
    }
}
