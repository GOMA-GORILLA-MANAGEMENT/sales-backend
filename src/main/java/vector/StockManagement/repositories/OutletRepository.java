package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.Outlet;

import java.util.List;
import java.util.Optional;

public interface OutletRepository extends JpaRepository<Outlet, Long> {

    @Query("select o from Outlet o where o.tenantId = :tenantId order by o.name")
    List<Outlet> findAllByTenant(Long tenantId);

    @Query("select o from Outlet o where o.id = :id and o.tenantId = :tenantId")
    Optional<Outlet> findByIdAndTenant(Long id, Long tenantId);

    @Query("select count(o) from Outlet o where o.tenantId = :tenantId")
    long countByTenant(Long tenantId);
}
