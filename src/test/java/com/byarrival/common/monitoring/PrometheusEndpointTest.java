package com.byarrival.common.monitoring;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PrometheusEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 프로메테우스가_인증_없이_메트릭을_수집할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("application=\"byarrival\"")))
            .andExpect(content().string(containsString("byarrival_batch_last_success")));
    }

    @Test
    void 그_밖의_actuator_엔드포인트는_열지_않는다() throws Exception {
        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().is4xxClientError());
    }
}
