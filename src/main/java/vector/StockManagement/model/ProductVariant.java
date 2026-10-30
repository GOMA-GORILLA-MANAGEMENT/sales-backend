package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A sellable and stockable form of a product (for example "Fanta Orange – Large").
 * Stock is always tracked in the smallest unit; a pack (crate, carton) is a multiple of it.
 */
@Entity
@Table(name = "product_variants")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String label = "Standard";

    @Column(name = "sell_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellPrice = BigDecimal.ZERO;

    @Column(name = "base_unit_name", nullable = false)
    private String baseUnitName = "piece";

    @Column(name = "pack_unit_name")
    private String packUnitName;

    @Column(name = "units_per_pack")
    private Integer unitsPerPack;

    /** Price when a whole pack is sold at once; null means pack price = units x sell price. */
    @Column(name = "pack_price", precision = 19, scale = 2)
    private BigDecimal packPrice;

    /** Dishes that are not counted in stock set this to false. */
    @Column(nullable = false)
    private boolean tracked = true;

    @Column(name = "low_stock_threshold")
    private Long lowStockThreshold = 0L;

    @Column(nullable = false)
    private boolean active = true;

    public boolean hasPack() {
        return packUnitName != null && unitsPerPack != null && unitsPerPack > 1;
    }

    public String displayName() {
        return product.getName() + (product.isHasSizes() ? " – " + label : "");
    }
}
