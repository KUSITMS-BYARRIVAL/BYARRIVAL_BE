package com.byarrival.domain.region;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byarrival.domain.auth.service.TokenService;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 근무 지역 목록(V20 시드)이 명세·디자인과 결정 사항대로 나오는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class RegionFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 시도는_17개를_화면_순서로_시군구는_이름순으로_돌려준다() throws Exception {
        regions()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(17))
            .andExpect(jsonPath("$.data[0].name").value("서울"))
            .andExpect(jsonPath("$.data[0].fullName").value("서울특별시"))
            .andExpect(jsonPath("$.data[1].name").value("경기"))
            .andExpect(jsonPath("$.data[2].name").value("인천"))
            .andExpect(jsonPath("$.data[9].name").value("강원"))
            .andExpect(jsonPath("$.data[16].name").value("제주"))
            .andExpect(jsonPath("$.data[0].sigungus.length()").value(25))
            .andExpect(jsonPath("$.data[0].sigungus[0].name").value("강남구"))
            .andExpect(jsonPath("$.data[0].sigungus[1].name").value("강동구"));
    }

    @Test
    void 일반구가_있는_시는_시까지만_세종은_하나만_인천은_개편된_구로_돌려준다() throws Exception {
        regions()
            .andExpect(jsonPath("$.data[1].sigungus.length()").value(31))
            .andExpect(jsonPath("$.data[1].sigungus[*].name", hasItem("수원시")))
            .andExpect(jsonPath("$.data[1].sigungus[*].name", not(hasItem("장안구"))))
            .andExpect(jsonPath("$.data[6].name").value("세종"))
            .andExpect(jsonPath("$.data[6].sigungus.length()").value(1))
            .andExpect(jsonPath("$.data[6].sigungus[0].name").value("세종특별자치시"))
            .andExpect(jsonPath("$.data[2].sigungus[*].name", hasItem("제물포구")))
            .andExpect(jsonPath("$.data[2].sigungus[*].name", hasItem("검단구")))
            .andExpect(jsonPath("$.data[2].sigungus[*].name", not(hasItem("중구"))))
            .andExpect(jsonPath("$.data[8].name").value("광주"))
            .andExpect(jsonPath("$.data[13].name").value("전남"));
    }

    @Test
    void 토큰_없이_조회하면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/regions")).andExpect(status().isUnauthorized());
    }

    private ResultActions regions() throws Exception {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        String accessToken = tokenService.issue(userId).accessToken();
        return mockMvc.perform(get("/api/v1/regions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }
}
