package com.byarrival.common.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.common.monitoring.ServerErrorNotifier;
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
    private ServerErrorNotifier serverErrorNotifier;

    @BeforeEach
    void setUp() {
        serverErrorNotifier = mock(ServerErrorNotifier.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler(serverErrorNotifier))
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

    @Test
    void 처리되지_않은_예외는_서버_에러_알림을_보낸다() throws Exception {
        mockMvc.perform(get("/test/unhandled"));

        verify(serverErrorNotifier).notify(eq("GET"), eq("/test/unhandled"), any(IllegalStateException.class));
    }

    @Test
    void 서버_오류_BusinessException은_서버_에러_알림을_보낸다() throws Exception {
        mockMvc.perform(get("/test/business-server-error"))
            .andExpect(status().isInternalServerError());

        verify(serverErrorNotifier).notify(eq("GET"), eq("/test/business-server-error"), any(BusinessException.class));
    }

    @Test
    void 클라이언트_오류는_서버_에러_알림을_보내지_않는다() throws Exception {
        mockMvc.perform(get("/test/business"));
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"\"}"));

        verify(serverErrorNotifier, never()).notify(any(), any(), any());
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

        @GetMapping("/test/business-server-error")
        void businessServerError() {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
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
