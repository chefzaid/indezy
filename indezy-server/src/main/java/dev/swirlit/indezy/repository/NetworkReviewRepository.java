package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkReview;
import dev.swirlit.indezy.model.enums.NetworkReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkReviewRepository extends JpaRepository<NetworkReview, Long> {

    List<NetworkReview> findByCompanyKeyAndStatusOrderByUpdatedAtDescIdDesc(String companyKey, NetworkReviewStatus status);

    Optional<NetworkReview> findByAuthorIdAndCompanyKey(Long authorId, String companyKey);

    List<NetworkReview> findByAuthorId(Long authorId);

    List<NetworkReview> findByStatusOrderByUpdatedAtAscIdAsc(NetworkReviewStatus status, Pageable page);
}
