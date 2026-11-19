package com.byarrival.domain.smalltalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import com.byarrival.domain.smalltalk.service.SmalltalkGenerator.SmalltalkSource;
import com.byarrival.domain.smalltalk.service.SmalltalkValidator;
import com.byarrival.domain.smalltalk.service.SmalltalkValidator.UnsuitableArticleException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmalltalkValidatorTest {

    private static final String TITLE = "앤트로픽 IPO에 공모물량 몇 배 투자 수요";
    private static final SmalltalkSource SOURCE = new SmalltalkSource(1L, 1L, TITLE, "IT·기술",
        "앤트로픽 IPO에 공모물량의 5배 수요가 몰렸습니다.",
        List.of("앤트로픽 상장에 투자 수요가 몰렸습니다.", "공모물량의 5배 주문이 들어왔습니다.", "상장일은 12월 3일입니다."));

    private final SmalltalkValidator validator = new SmalltalkValidator();

    @Test
    void 해요체_질문으로_끝나는_30자_이내_한_줄은_통과한다() {
        assertThat(validator.validate(new SmalltalkOutput(TITLE, true, " 요즘 앤트로픽 상장 얘기 들으셨어요? "), SOURCE))
            .isEqualTo("요즘 앤트로픽 상장 얘기 들으셨어요?");
        assertThat(validator.validate(new SmalltalkOutput(TITLE, true, "\"역대 최대 상장\"이라던데 들으셨어요?\""), SOURCE))
            .isEqualTo("\"역대 최대 상장\"이라던데 들으셨어요?\"");
    }

    @Test
    void 질문으로_끝나지_않으면_실패한다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "\"역대 최대 상장\"이라고 해요."), SOURCE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("해요체 질문");
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "상장 소식 들었어?"), SOURCE))
            .hasMessageContaining("해요체 질문");
    }

    @Test
    void 대화_소재로_알맞지_않은_기사는_따로_알린다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, false, ""), SOURCE))
            .isInstanceOf(UnsuitableArticleException.class);
    }

    @Test
    void 줄바꿈이_들어간_한_줄은_실패한다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "소식 들으셨어요?\n같이 얘기해요?"), SOURCE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("줄바꿈");
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "소식 들으셨어요?\r\n좋아요."), SOURCE))
            .hasMessageContaining("줄바꿈");
    }

    @Test
    void 길거나_해요체가_아니거나_제목이_다르면_실패한다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput("다른 제목", true, "가".repeat(31) + "다."), SOURCE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("title을 입력받은 제목과 똑같이")
            .hasMessageContaining("hook이 33자")
            .hasMessageContaining("해요체 질문");
    }

    @Test
    void 요약에_있는_숫자는_통과한다() {
        assertThat(validator.validate(new SmalltalkOutput(TITLE, true, "공모에 5배 수요가 몰렸대요, 들으셨어요?"), SOURCE))
            .isEqualTo("공모에 5배 수요가 몰렸대요, 들으셨어요?");
    }

    @Test
    void 요약에_없는_숫자를_쓰면_실패한다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "공모에 7배 수요가 몰렸대요, 들으셨어요?"),
            SOURCE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("hook의 숫자 [7]가 요약에 없습니다");
    }
}
