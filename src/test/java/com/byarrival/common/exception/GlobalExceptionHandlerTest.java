package com.byarrival.common.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void 성공_응답은_공통_형식으로_반환된다() throws Exception {
        mockMvc.perform(get("/test/success"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void BusinessException은_에러코드의_상태와_메시지로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value(CommonErrorCode.FORBIDDEN.getMessage()))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 검증_실패는_400과_필드별_에러를_반환한다() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].field").value("name"));
    }

    @Test
    void 잘못된_JSON은_400을_반환한다() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void 지원하지_않는_메서드는_405를_반환한다() throws Exception {
        mockMvc.perform(post("/test/success"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 처리되지_않은_예외는_500을_반환한다() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/success")
        ApiResponse<String> success() {
            return ApiResponse.ok("ok");
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unhandled")
        void unhandled() {
            throw new IllegalStateException("unexpected");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
