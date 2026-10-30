package vector.StockManagement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/** A business (restaurant account) on the platform. Suspended when {@code active} is false. */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Tenant extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    private String phone;
    private String email;
    private String address;

    @Size(max = 50)
    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active")
    private Boolean active = true;

    /** When false, orders are approved as soon as a waiter sends them. */
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = true;

    /** When true, stock may go below zero (otherwise delivery is refused). */
    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock = false;

    @ElementCollection
    @CollectionTable(name = "tenant_settings", joinColumns = @JoinColumn(name = "tenant_id"))
    @MapKeyColumn(name = "setting_key")
    @Column(name = "setting_value", length = 1000)
    private Map<String, String> settings = new HashMap<>();

    public Tenant(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public boolean isActive() {
        return active == null || active;
    }
}
