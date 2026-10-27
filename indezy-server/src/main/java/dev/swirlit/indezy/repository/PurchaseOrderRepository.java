package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /** A mission's orders and amendments, in the order they were written. */
    List<PurchaseOrder> findByProjectIdOrderByIdAsc(Long projectId);

    /** Every order and amendment of a workspace, for its alerts and the account data export. */
    List<PurchaseOrder> findByProjectFreelanceIdOrderByIdAsc(Long freelanceId);

    Optional<PurchaseOrder> findByIdAndProjectFreelanceId(Long id, Long freelanceId);

    List<PurchaseOrder> findByAmendsId(Long amendsId);

    /** Removes a mission's orders before the mission itself is deleted. */
    void deleteByProjectId(Long projectId);
}
