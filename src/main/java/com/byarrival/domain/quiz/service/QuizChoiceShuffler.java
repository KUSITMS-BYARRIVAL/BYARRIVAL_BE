package com.byarrival.domain.quiz.service;

import com.byarrival.domain.quiz.service.QuizValidator.CheckedQuiz;
import com.byarrival.domain.quiz.service.QuizValidator.ValidatedQuiz;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 정답과 오답을 무작위로 섞어 선택지 순서와 정답 번호를 정한다. AI에게 번호를 맡기면 정답이 한 자리에 몰리고,
 * 번호와 해설이 가리키는 정답이 어긋나기도 해서(2026-11-14 운영 점검) 서버가 정한다.
 */
@Component
public class QuizChoiceShuffler {

    private final Random random;

    public QuizChoiceShuffler(@Qualifier("quizRandom") Random random) {
        this.random = random;
    }

    public ValidatedQuiz shuffle(CheckedQuiz quiz) {
        List<String> choices = new ArrayList<>(quiz.wrongAnswers());
        choices.add(quiz.answer());
        Collections.shuffle(choices, random);
        return new ValidatedQuiz(quiz.question(), List.copyOf(choices), choices.indexOf(quiz.answer()) + 1,
            quiz.explanation());
    }
}
