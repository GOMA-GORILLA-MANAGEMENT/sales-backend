package vector.StockManagement.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-business counter used for readable order and receipt numbers. */
@Entity
@Table(name = "sequences", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "seq_name"}))
@Getter
@Setter
@NoArgsConstructor
public class Sequence extends TenantEntity {

    @Column(name = "seq_name", nullable = false)
    private String name;

    @Column(name = "current_value", nullable = false)
    private long value;
}
