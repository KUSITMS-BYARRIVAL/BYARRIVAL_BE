package com.byarrival.common.monitoring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServerErrorAlertFilterTest {

    private final ServerErrorNotifier serverErrorNotifier = mock(ServerErrorNotifier.class);
    private final ServerErrorAlertFilter filter = new ServerErrorAlertFilter(serverErrorNotifier);

    @Test
    void 필터에서_난_예외를_알리고_그대로_던진다() {
        IllegalStateException error = new IllegalStateException("폼 디코딩 실패");

        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), failingChain(error)))
            .isSameAs(error);
        verify(serverErrorNotifier).notify("PUT", "/api/v1/users/me", error);
    }

    @Test
    void ServletException으로_감싸진_예외는_원인을_알린다() {
        IllegalStateException cause = new IllegalStateException("원인");
        ServletException wrapped = new ServletException("감싼 예외", cause);

        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), failingChain(wrapped)))
            .isSameAs(wrapped);
        verify(serverErrorNotifier).notify("PUT", "/api/v1/users/me", cause);
    }

    @Test
    void 클라이언트_연결_끊김_같은_IOException은_알리지_않는다() {
        IOException brokenPipe = new IOException("Broken pipe");

        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), failingChain(brokenPipe)))
            .isSameAs(brokenPipe);
        verify(serverErrorNotifier, never()).notify(any(), any(), any());
    }

    @Test
    void 정상_요청은_알리지_않는다() throws Exception {
        filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());

        verify(serverErrorNotifier, never()).notify(any(), any(), any());
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("PUT", "/api/v1/users/me");
    }

    private FilterChain failingChain(Exception error) {
        return (request, response) -> {
            if (error instanceof IOException ioException) {
                throw ioException;
            }
            if (error instanceof ServletException servletException) {
                throw servletException;
            }
            throw (RuntimeException) error;
        };
    }
}
