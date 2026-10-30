package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.OrderLine;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    @Query("select l from OrderLine l join fetch l.order o "
            + "where l.tenantId = :tenantId and o.outlet.id in :outletIds "
            + "and o.status = 'DELIVERED' and o.deliveredAt >= :from and o.deliveredAt < :to")
    List<OrderLine> deliveredLines(Long tenantId, java.util.Collection<Long> outletIds, LocalDateTime from, LocalDateTime to);
}
