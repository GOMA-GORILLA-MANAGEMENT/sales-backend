package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.util.ArrayList;
import java.util.List;

/** A menu item. When {@code hasSizes} is false it has a single "Standard" variant. */
@Entity
@Table(name = "products")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Product extends TenantEntity {

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(length = 500)
    private String description;

    @Column(name = "has_sizes", nullable = false)
    private boolean hasSizes;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductVariant> variants = new ArrayList<>();
}
