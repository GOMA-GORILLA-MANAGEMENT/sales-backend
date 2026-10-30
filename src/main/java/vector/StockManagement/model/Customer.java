package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

@Entity
@Table(name = "customers")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends TenantEntity {

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;

    @Column(name = "id_number")
    private String idNumber;

    /** Maximum total outstanding debt; zero means no credit. */
    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    /** Days a debt may stay open before it is overdue. */
    @Column(name = "credit_days", nullable = false)
    private int creditDays = 30;

    @Column(nullable = false)
    private boolean active = true;
}
