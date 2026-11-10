package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 노선 생성에 쓰는 이동시간·관심 분야 조회 전용 쿼리. 값 저장은 온보딩·마이 설정이 맡는다.
 */
public interface UserTravelSettingRepository extends Repository<User, Long> {

    @Query(value = "SELECT default_commute_minutes AS defaultMinutes, mon_minutes AS monMinutes, "
        + "tue_minutes AS tueMinutes, wed_minutes AS wedMinutes, thu_minutes AS thuMinutes, "
        + "fri_minutes AS friMinutes, sat_minutes AS satMinutes, sun_minutes AS sunMinutes "
        + "FROM users WHERE id = :userId AND status = 'ACTIVE'", nativeQuery = true)
    Optional<TravelMinutes> findTravelMinutes(@Param("userId") Long userId);

    @Query(value = "SELECT category_id FROM user_interest_category WHERE user_id = :userId ORDER BY id",
        nativeQuery = true)
    List<Long> findInterestCategoryIds(@Param("userId") Long userId);

    interface TravelMinutes {

        Integer getDefaultMinutes();

        Integer getMonMinutes();

        Integer getTueMinutes();

        Integer getWedMinutes();

        Integer getThuMinutes();

        Integer getFriMinutes();

        Integer getSatMinutes();

        Integer getSunMinutes();
    }
}
