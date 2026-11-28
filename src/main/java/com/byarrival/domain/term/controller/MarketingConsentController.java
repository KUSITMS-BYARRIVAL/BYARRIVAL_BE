package com.byarrival.domain.term.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.term.dto.request.MarketingConsentRequest;
import com.byarrival.domain.term.dto.response.MarketingConsentResponse;
import com.byarrival.domain.term.service.MarketingConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Marketing Consent", description = "광고성 정보 수신 동의")
@RestController
@RequestMapping("/api/v1/users/me/marketing-consent")
@RequiredArgsConstructor
public class MarketingConsentController {

    private final MarketingConsentService marketingConsentService;

    @Operation(summary = "내 광고성 정보 수신 동의", description = "동의하거나 철회한 적이 없으면 false다.")
    @GetMapping
    public ApiResponse<MarketingConsentResponse> getConsent(@LoginUserId Long userId) {
        return ApiResponse.ok(marketingConsentService.getConsent(userId));
    }

    @Operation(summary = "광고성 정보 수신 동의 변경",
        description = "두 값을 모두 보낸다. 야간(21~08시) 동의는 광고성 정보 수신에 동의해야 true로 할 수 있다(아니면 400). "
            + "광고성 정보 수신을 철회하면 야간 동의도 false로 보낸다. 바뀐 값이 있으면 처리 결과를 알림함으로 안내한다.")
    @PutMapping
    public ApiResponse<MarketingConsentResponse> change(@LoginUserId Long userId,
        @Valid @RequestBody MarketingConsentRequest request) {
        return ApiResponse.ok(marketingConsentService.change(userId, request.marketing(), request.night()));
    }
}
