package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.StockMovement;

import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select m from StockMovement m join fetch m.variant v join fetch v.product join fetch m.outlet o "
            + "where m.tenantId = :tenantId and o.id in :outletIds "
            + "and m.createdAt >= :from and m.createdAt < :to order by m.id desc")
    List<StockMovement> findInPeriod(Long tenantId, java.util.Collection<Long> outletIds, LocalDateTime from, LocalDateTime to);
}
