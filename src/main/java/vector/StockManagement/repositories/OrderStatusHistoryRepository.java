package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.OrderStatusHistory;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    @Query("select h from OrderStatusHistory h where h.tenantId = :tenantId and h.order.id = :orderId order by h.id")
    List<OrderStatusHistory> findForOrder(Long tenantId, Long orderId);
}
