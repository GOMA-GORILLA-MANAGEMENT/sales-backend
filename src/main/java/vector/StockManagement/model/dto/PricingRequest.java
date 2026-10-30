package vector.StockManagement.model.dto;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Builder;
import lombok.Data;
import vector.StockManagement.model.enums.BusinessType;
import vector.StockManagement.model.enums.PricingType;

@Data
@Builder
public class PricingRequest {
    private String businessName;
    private String fullName;
    private String email;
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    private PricingType pricingType;
}
