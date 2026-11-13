package com.byarrival.domain.smalltalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.smalltalk.dto.SmalltalkOutput;
import com.byarrival.domain.smalltalk.service.SmalltalkValidator;
import com.byarrival.domain.smalltalk.service.SmalltalkValidator.UnsuitableArticleException;
import org.junit.jupiter.api.Test;

class SmalltalkValidatorTest {

    private static final String TITLE = "앤트로픽 IPO에 공모물량 몇 배 투자 수요";

    private final SmalltalkValidator validator = new SmalltalkValidator();

    @Test
    void 해요체_30자_이내_한_줄은_통과한다() {
        assertThat(validator.validate(new SmalltalkOutput(TITLE, true, " 요즘 앤트로픽 상장 얘기 들으셨어요? "), TITLE))
            .isEqualTo("요즘 앤트로픽 상장 얘기 들으셨어요?");
        assertThat(validator.validate(new SmalltalkOutput(TITLE, true, "\"역대 최대 상장\"이라고 해요."), TITLE))
            .isEqualTo("\"역대 최대 상장\"이라고 해요.");
    }

    @Test
    void 대화_소재로_알맞지_않은_기사는_따로_알린다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, false, ""), TITLE))
            .isInstanceOf(UnsuitableArticleException.class);
    }

    @Test
    void 줄바꿈이_들어간_한_줄은_실패한다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "소식 들으셨어요?\n같이 얘기해요?"), TITLE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("줄바꿈");
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput(TITLE, true, "소식 들으셨어요?\r\n좋아요."), TITLE))
            .hasMessageContaining("줄바꿈");
    }

    @Test
    void 길거나_해요체가_아니거나_제목이_다르면_실패한다() {
        assertThatThrownBy(() -> validator.validate(new SmalltalkOutput("다른 제목", true, "가".repeat(31) + "다."), TITLE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("title을 입력받은 제목과 똑같이")
            .hasMessageContaining("hook이 33자")
            .hasMessageContaining("해요체");
    }
}
