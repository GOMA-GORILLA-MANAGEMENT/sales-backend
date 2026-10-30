package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import vector.StockManagement.model.enums.OutletStatus;
import vector.StockManagement.model.enums.OutletType;

@Entity
@Table(name = "outlets")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Outlet extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutletType type = OutletType.RESTAURANT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutletStatus status = OutletStatus.OPEN;

    private String address;
    private String phone;
}
