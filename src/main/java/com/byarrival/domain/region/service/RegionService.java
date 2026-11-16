package com.byarrival.domain.region.service;

import com.byarrival.domain.region.dto.response.RegionResponse;
import com.byarrival.domain.region.dto.response.RegionSidoResponse;
import com.byarrival.domain.region.dto.response.RegionSidoResponse.SigunguResponse;
import com.byarrival.domain.region.entity.RegionSido;
import com.byarrival.domain.region.entity.RegionSigungu;
import com.byarrival.domain.region.repository.RegionSidoRepository;
import com.byarrival.domain.region.repository.RegionSigunguRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무 지역 목록 조회와 회원이 고른 지역 확인(ONB-12, MY-12).
 */
@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionSidoRepository regionSidoRepository;
    private final RegionSigunguRepository regionSigunguRepository;

    /**
     * 시/도는 화면 순서, 시/군/구는 이름순으로 모두 돌려준다. 검색은 앱이 이 목록에서 거른다.
     */
    @Transactional(readOnly = true)
    public List<RegionSidoResponse> getRegions() {
        Map<Short, List<SigunguResponse>> sigungus = regionSigunguRepository.findAll().stream()
            .sorted(Comparator.comparing(RegionSigungu::getName))
            .collect(Collectors.groupingBy(RegionSigungu::getSidoId,
                Collectors.mapping(sigungu -> new SigunguResponse(sigungu.getId(), sigungu.getName()),
                    Collectors.toList())));
        return regionSidoRepository.findAll(Sort.by("id")).stream()
            .map(sido -> new RegionSidoResponse(sido.getId(), sido.getName(), sido.getFullName(),
                sigungus.getOrDefault(sido.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean exists(Integer sigunguId) {
        return regionSigunguRepository.existsById(sigunguId);
    }

    /**
     * 고르지 않았으면(null) 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<RegionResponse> findRegion(Integer sigunguId) {
        if (sigunguId == null) {
            return Optional.empty();
        }
        return regionSigunguRepository.findById(sigunguId).flatMap(sigungu ->
            regionSidoRepository.findById(sigungu.getSidoId()).map(sido -> toResponse(sido, sigungu)));
    }

    private static RegionResponse toResponse(RegionSido sido, RegionSigungu sigungu) {
        return new RegionResponse(sido.getId(), sido.getName(), sigungu.getId(), sigungu.getName());
    }
}
