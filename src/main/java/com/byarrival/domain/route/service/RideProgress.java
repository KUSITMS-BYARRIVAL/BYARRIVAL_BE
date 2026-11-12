package com.byarrival.domain.route.service;

import com.byarrival.domain.route.entity.RouteCard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 운행 진행 상태(READ-06). 정거장은 담은 순서대로 1번부터 센다. 숨겼거나 원문이 삭제된 기사는 건너뛴다(READ-01).
 *
 * @param stops            담은 카드 (담은 순서)
 * @param unavailableIds   건너뛸 기사 ID
 * @param readCount        완독한 기사 수
 * @param currentStop      이어서 볼 정거장 번호. 모두 읽었으면 마지막 정거장
 * @param remainingStops   남은 기사 수 (건너뛸 기사 제외)
 * @param remainingMinutes 남은 기사의 예상 읽기시간 합(분, 올림)
 * @param arrivable        남은 기사가 없어 도착할 수 있는지
 */
public record RideProgress(
    List<RouteCard> stops,
    Set<Long> unavailableIds,
    int readCount,
    int currentStop,
    int remainingStops,
    int remainingMinutes,
    boolean arrivable
) {

    public static RideProgress of(List<RouteCard> cards, Set<Long> unavailableIds) {
        List<RouteCard> stops = cards.stream()
            .filter(RouteCard::isCarted)
            .sorted(Comparator.comparing(RouteCard::getCartOrder))
            .toList();
        int readCount = (int) stops.stream().filter(RouteCard::isRead).count();
        List<RouteCard> remaining = stops.stream()
            .filter(card -> !card.isRead() && !unavailableIds.contains(card.getArticleId()))
            .toList();
        int currentStop = remaining.isEmpty() ? stops.size() : stops.indexOf(remaining.getFirst()) + 1;
        BigDecimal minutes = remaining.stream()
            .map(RouteCard::getReadingMinutes)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RideProgress(stops, Set.copyOf(unavailableIds), readCount, currentStop, remaining.size(),
            minutes.setScale(0, RoundingMode.CEILING).intValue(), remaining.isEmpty());
    }

    public int totalStops() {
        return stops.size();
    }

    /**
     * @param stopNumber 1부터 시작하는 정거장 번호
     */
    public RouteCard stop(int stopNumber) {
        return stops.get(stopNumber - 1);
    }

    public boolean hasStop(int stopNumber) {
        return stopNumber >= 1 && stopNumber <= stops.size();
    }

    public boolean isAvailable(RouteCard card) {
        return !unavailableIds.contains(card.getArticleId());
    }
}
