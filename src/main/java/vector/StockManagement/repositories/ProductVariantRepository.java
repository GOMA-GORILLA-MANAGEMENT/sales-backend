package vector.StockManagement.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vector.StockManagement.model.ProductVariant;

import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Query("select v from ProductVariant v join fetch v.product where v.id = :id and v.tenantId = :tenantId")
    Optional<ProductVariant> findByIdAndTenant(Long id, Long tenantId);
}
