package vector.StockManagement.config;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = Long.class)
)
public class TenantFilterConfig {
    @Id
    private Long id; // just a placeholder
}

