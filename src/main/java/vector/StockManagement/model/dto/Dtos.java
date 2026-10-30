package vector.StockManagement.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vector.StockManagement.model.enums.*;

import java.math.BigDecimal;
import java.util.List;

/** Request bodies. */
public final class Dtos {
    private Dtos() {}

    public record OutletRequest(@NotBlank String name, OutletType type, String address, String phone) {}

    public record OutletStatusRequest(@NotNull OutletStatus status) {}

    public record StaffRequest(@NotBlank String firstName, @NotBlank String lastName, @NotBlank @Email String email,
                               String phone, @NotNull Role role, List<Long> outletIds, String password, String gender) {}

    public record StaffUpdateRequest(String firstName, String lastName, String phone, Role role,
                                     List<Long> outletIds, UserStatus status) {}

    public record VariantRequest(Long id, String label, @NotNull @PositiveOrZero BigDecimal sellPrice,
                                 String baseUnitName, String packUnitName, Integer unitsPerPack,
                                 BigDecimal packPrice, Boolean tracked, Long lowStockThreshold, Boolean active) {}

    public record ProductRequest(@NotBlank String name, String category, String description, boolean hasSizes,
                                 Boolean active, @NotEmpty @Valid List<VariantRequest> variants) {}

    public record ReceiveStockRequest(@NotNull Long outletId, @NotNull Long variantId, @Positive long quantity,
                                      @NotNull UnitType unit, @Positive Integer unitsPerPackOverride,
                                      @PositiveOrZero BigDecimal purchasePrice, String note) {}

    public record TransferRequest(@NotNull Long fromOutletId, @NotNull Long toOutletId, @NotNull Long variantId,
                                  @Positive long quantity, @NotNull UnitType unit, String note) {}

    public record AdjustRequest(@NotNull Long outletId, @NotNull Long variantId, @NotNull MovementType type,
                                @PositiveOrZero long quantity, @NotNull UnitType unit, @NotBlank String reason) {}

    public record CustomerRequest(@NotBlank String name, String phone, String email, String idNumber,
                                  @PositiveOrZero BigDecimal creditLimit, @PositiveOrZero Integer creditDays,
                                  Boolean active) {}

    public record QuickCustomerRequest(String name, String phone) {}

    public record OrderLineRequest(@NotNull Long variantId, @NotNull UnitType unit, @Positive int quantity) {}

    public record OrderRequest(@NotNull Long outletId, Long customerId, @NotNull PaymentMethod paymentMethod,
                               String tableLabel, @NotEmpty @Valid List<OrderLineRequest> lines) {}

    public record ReasonRequest(String reason) {}

    public record DeliverRequest(@NotNull @PositiveOrZero BigDecimal amountPaid, Long customerId,
                                 PaymentMethod paymentMethod, String overrideReason) {}

    public record DebtPaymentRequest(@NotNull @Positive BigDecimal amount, @NotNull PaymentMethod method) {}

    public record DebtAdjustRequest(@NotNull AdjustmentType type, @NotNull BigDecimal amount, @NotBlank String reason) {}

    public record ExpenseRequest(@NotNull Long outletId, @NotBlank String category, String description,
                                 @NotNull @Positive BigDecimal amount, java.time.LocalDate expenseDate) {}

    public record SettingsRequest(Boolean approvalRequired, Boolean allowNegativeStock, String name) {}

    public record OnboardRequest(@NotBlank String businessName, @NotBlank String outletName, OutletType outletType,
                                 @NotBlank String ownerFirstName, @NotBlank String ownerLastName,
                                 @NotBlank @Email String ownerEmail, String ownerPhone, String password) {}

    public record UpdateRestaurantRequest(@NotBlank String name, String phone, String address,
                                          String ownerFirstName, String ownerLastName,
                                          @Email String ownerEmail, String ownerPhone) {}

    public record ResetPasswordForOwnerRequest(String password) {}
}
