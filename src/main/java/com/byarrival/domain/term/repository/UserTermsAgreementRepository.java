package com.byarrival.domain.term.repository;

import com.byarrival.domain.term.entity.UserTermsAgreement;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

    List<UserTermsAgreement> findByUserIdAndTermsIdInOrderByAgreedAtDescIdDesc(
        Long userId, Collection<Long> termsIds);
}
