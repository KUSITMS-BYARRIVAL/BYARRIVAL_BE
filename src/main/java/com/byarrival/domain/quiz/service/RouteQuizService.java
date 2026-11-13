package com.byarrival.domain.quiz.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.quiz.dto.response.QuizAnswerResponse;
import com.byarrival.domain.quiz.dto.response.QuizChoiceResponse;
import com.byarrival.domain.quiz.dto.response.QuizProgressResponse;
import com.byarrival.domain.quiz.dto.response.RouteQuizResponse;
import com.byarrival.domain.quiz.dto.response.RouteQuizSetResponse;
import com.byarrival.domain.quiz.entity.Quiz;
import com.byarrival.domain.quiz.entity.QuizChoice;
import com.byarrival.domain.quiz.entity.RouteQuiz;
import com.byarrival.domain.quiz.exception.QuizErrorCode;
import com.byarrival.domain.quiz.repository.QuizRepository;
import com.byarrival.domain.quiz.repository.RouteQuizRepository;
import com.byarrival.domain.route.service.RouteReadQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 퀴즈(QUIZ-01~03). 도착한 노선에서 완독한 기사의 퀴즈 중 최대 3문제를 처음 열 때 무작위로 골라 고정하고,
 * 정거장 순서대로 낸다. 답은 문제당 한 번만 저장하며, 안 푼 문제는 다시 들어오면 이어서 푼다.
 */
@Service
public class RouteQuizService {

    public static final int QUIZ_COUNT = 3;

    private final RouteReadQueryService routeReadQueryService;
    private final QuizRepository quizRepository;
    private final RouteQuizRepository routeQuizRepository;
    private final RouteQuizWriter routeQuizWriter;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final Random random;
    private final Clock clock;

    public RouteQuizService(RouteReadQueryService routeReadQueryService, QuizRepository quizRepository,
        RouteQuizRepository routeQuizRepository, RouteQuizWriter routeQuizWriter,
        ArticleQueryService articleQueryService, CategoryMappingService categoryMappingService,
        @Qualifier("quizRandom") Random random, Clock clock) {
        this.routeReadQueryService = routeReadQueryService;
        this.quizRepository = quizRepository;
        this.routeQuizRepository = routeQuizRepository;
        this.routeQuizWriter = routeQuizWriter;
        this.articleQueryService = articleQueryService;
        this.categoryMappingService = categoryMappingService;
        this.random = random;
        this.clock = clock;
    }

    public RouteQuizSetResponse getQuizzes(Long userId, Long routeId) {
        List<Long> readArticleIds = routeReadQueryService.findReadArticleIdsOfCompleted(userId, routeId);
        if (routeQuizRepository.findAllByRouteIdOrderByQuizOrder(routeId).isEmpty()) {
            List<Long> picked = pick(readArticleIds);
            if (!picked.isEmpty()) {
                try {
                    routeQuizWriter.fix(routeId, picked, LocalDateTime.now(clock));
                } catch (DataIntegrityViolationException e) {
                    // 같은 노선을 동시에 처음 연 경우. 먼저 고정된 문제를 쓴다
                }
            }
        }
        return read(routeId);
    }

    @Transactional
    public QuizAnswerResponse answer(Long userId, Long routeId, Long quizId, int choiceSeq) {
        routeReadQueryService.findReadArticleIdsOfCompleted(userId, routeId);
        RouteQuiz routeQuiz = routeQuizRepository.findByRouteIdAndQuizId(routeId, quizId)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.QUIZ_NOT_FOUND));
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.QUIZ_NOT_FOUND));
        boolean validChoice = QuizStore.choicesOf(quiz).stream().anyMatch(choice -> choice.seq() == choiceSeq);
        if (!validChoice) {
            throw new BusinessException(QuizErrorCode.QUIZ_CHOICE_NOT_FOUND);
        }
        if (!routeQuiz.isAnswered()) {
            routeQuizRepository.answerIfUnanswered(routeQuiz.getId(), choiceSeq, quiz.isCorrect(choiceSeq),
                LocalDateTime.now(clock));
        }
        List<RouteQuiz> all = routeQuizRepository.findAllByRouteIdOrderByQuizOrder(routeId);
        RouteQuiz saved = all.stream().filter(item -> item.getQuizId().equals(quizId)).findFirst().orElseThrow();
        return new QuizAnswerResponse(quizId, saved.getSelectedSeq(), quiz.getAnswerSeq(),
            Boolean.TRUE.equals(saved.getCorrect()), quiz.getExplanation(), progressOf(all));
    }

    /**
     * 퀴즈 결과(QUIZ-03). 퀴즈를 연 적이 없으면 total 0이다.
     */
    @Transactional(readOnly = true)
    public QuizProgressResponse getResult(Long userId, Long routeId) {
        routeReadQueryService.findReadArticleIdsOfCompleted(userId, routeId);
        return progressOf(routeQuizRepository.findAllByRouteIdOrderByQuizOrder(routeId));
    }

    // 완독 기사의 퀴즈 중 최대 3문제를 무작위로 골라 정거장 순서대로 둔다
    private List<Long> pick(List<Long> readArticleIds) {
        if (readArticleIds.isEmpty()) {
            return List.of();
        }
        List<Quiz> candidates = new ArrayList<>(quizRepository.findActiveByArticleIds(readArticleIds));
        candidates.sort(Comparator.comparing(Quiz::getId));
        Collections.shuffle(candidates, random);
        List<Quiz> picked = new ArrayList<>(candidates.subList(0, Math.min(QUIZ_COUNT, candidates.size())));
        picked.sort(Comparator.comparingInt(quiz -> readArticleIds.indexOf(quiz.getArticleId())));
        return picked.stream().map(Quiz::getId).toList();
    }

    private RouteQuizSetResponse read(Long routeId) {
        List<RouteQuiz> routeQuizzes = routeQuizRepository.findAllByRouteIdOrderByQuizOrder(routeId);
        Map<Long, Quiz> quizzes = quizRepository.findAllById(routeQuizzes.stream().map(RouteQuiz::getQuizId).toList())
            .stream().collect(Collectors.toMap(Quiz::getId, Function.identity()));
        Map<Long, Article> articles = articleQueryService.findAllByIds(
            quizzes.values().stream().map(Quiz::getArticleId).toList());
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();

        List<RouteQuizResponse> items = new ArrayList<>();
        for (RouteQuiz routeQuiz : routeQuizzes) {
            Quiz quiz = quizzes.get(routeQuiz.getQuizId());
            Article article = articles.get(quiz.getArticleId());
            boolean answered = routeQuiz.isAnswered();
            List<QuizChoiceResponse> choices = QuizStore.choicesOf(quiz).stream()
                .sorted(Comparator.comparingInt(QuizChoice::seq))
                .map(choice -> new QuizChoiceResponse(choice.seq(), choice.content()))
                .toList();
            items.add(new RouteQuizResponse(routeQuiz.getQuizOrder(), quiz.getId(),
                article == null ? null : article.getCategoryId(),
                article == null ? null : categoryNames.get(article.getCategoryId()), quiz.getArticleId(),
                article == null ? null : article.getTitle(), quiz.getQuestion(), choices, answered,
                routeQuiz.getSelectedSeq(), routeQuiz.getCorrect(), answered ? quiz.getAnswerSeq() : null,
                answered ? quiz.getExplanation() : null));
        }
        Integer next = routeQuizzes.stream().filter(item -> !item.isAnswered()).map(RouteQuiz::getQuizOrder)
            .findFirst().orElse(null);
        return new RouteQuizSetResponse(routeId, next, items, progressOf(routeQuizzes));
    }

    private static QuizProgressResponse progressOf(List<RouteQuiz> routeQuizzes) {
        int answered = (int) routeQuizzes.stream().filter(RouteQuiz::isAnswered).count();
        int correct = (int) routeQuizzes.stream().filter(item -> Boolean.TRUE.equals(item.getCorrect())).count();
        return new QuizProgressResponse(routeQuizzes.size(), answered, correct,
            !routeQuizzes.isEmpty() && answered == routeQuizzes.size());
    }
}
