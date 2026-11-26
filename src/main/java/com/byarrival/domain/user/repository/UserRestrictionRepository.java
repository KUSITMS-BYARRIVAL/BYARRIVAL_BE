package com.byarrival.domain.user.repository;

import com.byarrival.domain.user.entity.UserRestriction;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRestrictionRepository extends JpaRepository<UserRestriction, Long> {

    String ACTIVE = "r.userId = :userId AND r.releasedAt IS NULL AND r.startsAt <= :now "
        + "AND (r.endsAt IS NULL OR r.endsAt > :now)";

    // 회원 행을 잠근 뒤에만 새로 만들므로 하나뿐이지만, 혹시 여럿이면 최근 것부터 돌려준다
    @Query("SELECT r FROM UserRestriction r WHERE " + ACTIVE + " ORDER BY r.startsAt DESC, r.id DESC")
    List<UserRestriction> findActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(r) > 0 FROM UserRestriction r WHERE " + ACTIVE)
    boolean existsActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    List<UserRestriction> findAllByUserIdOrderByStartsAtDescIdDesc(Long userId);
}
