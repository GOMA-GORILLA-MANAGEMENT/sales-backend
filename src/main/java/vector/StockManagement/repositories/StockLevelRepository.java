package vector.StockManagement.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.StockLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.tenantId = :tenantId and s.outlet.id = :outletId and s.variant.id = :variantId")
    Optional<StockLevel> findForUpdate(Long tenantId, Long outletId, Long variantId);

    @Query("select s from StockLevel s where s.tenantId = :tenantId and s.outlet.id = :outletId and s.variant.id = :variantId")
    Optional<StockLevel> find(Long tenantId, Long outletId, Long variantId);

    @Query("select s from StockLevel s join fetch s.variant v join fetch v.product join fetch s.outlet o "
            + "where s.tenantId = :tenantId and o.id in :outletIds order by v.product.name, v.label")
    List<StockLevel> findAllFor(Long tenantId, java.util.Collection<Long> outletIds);

    @Query("select coalesce(sum(s.quantity * s.avgUnitCost), 0) from StockLevel s "
            + "where s.tenantId = :tenantId and s.outlet.id in :outletIds")
    BigDecimal stockValue(Long tenantId, java.util.Collection<Long> outletIds);
}
