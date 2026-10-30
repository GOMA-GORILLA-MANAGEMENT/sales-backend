package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.*;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.MovementType;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.model.enums.UnitType;
import vector.StockManagement.repositories.OutletRepository;
import vector.StockManagement.repositories.ProductVariantRepository;
import vector.StockManagement.repositories.StockLevelRepository;
import vector.StockManagement.repositories.StockMovementRepository;
import vector.StockManagement.repositories.TenantRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Stock is kept in the smallest unit. Anything entered in packs (crates, cartons) is converted on the way in,
 * and both the entered and the converted quantity are written to the movement ledger.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockLevelRepository levelRepository;
    private final StockMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;
    private final OutletRepository outletRepository;
    private final TenantRepository tenantRepository;
    private final Access access;
    private final AuditService audit;

    // ---------- conversions ----------

    /** Smallest units per entered unit. A receipt may override the product's default pack size. */
    public int unitsPer(ProductVariant variant, UnitType unit, Integer packSizeOverride) {
        if (unit == UnitType.BASE) return 1;
        int size = packSizeOverride != null ? packSizeOverride
                : (variant.hasPack() ? variant.getUnitsPerPack() : 0);
        if (size < 2) {
            throw ApiException.badRequest(variant.displayName() + " is not set up to be handled in packs");
        }
        return size;
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<Views.StockRow> levels(Long outletId, boolean lowOnly) {
        access.require(Permission.STOCK_VIEW);
        Set<Long> scope = access.scopeOutlets(outletId);
        return levelRepository.findAllFor(access.tenantId(), scope).stream()
                .filter(s -> s.getVariant().isTracked())
                .map(Views::of)
                .filter(r -> !lowOnly || r.low())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Views.MovementView> movements(Long outletId, LocalDate from, LocalDate to) {
        access.require(Permission.STOCK_VIEW);
        Set<Long> scope = access.scopeOutlets(outletId);
        LocalDate start = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return movementRepository.findInPeriod(access.tenantId(), scope, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream().map(Views::of).toList();
    }

    // ---------- writes ----------

    @Transactional
    public Views.StockRow receive(Dtos.ReceiveStockRequest request) {
        access.require(Permission.STOCK_RECEIVE);
        Long tenantId = access.tenantId();
        Outlet outlet = access.outlet(request.outletId());
        ProductVariant variant = trackedVariant(request.variantId(), tenantId);
        int upp = unitsPer(variant, request.unit(), request.unitsPerPackOverride());
        long base = Math.multiplyExact(request.quantity(), (long) upp);
        BigDecimal unitCost = request.purchasePrice() == null ? null
                : request.purchasePrice().divide(BigDecimal.valueOf(upp), 4, RoundingMode.HALF_UP);

        StockLevel level = lockLevel(tenantId, outlet, variant);
        StockMovement m = apply(level, outlet, variant, MovementType.RECEIVE, request.quantity(), request.unit(), upp,
                base, unitCost, request.note(), null, true);
        audit.log("stock.received", "StockMovement", m.getId(), variant.displayName() + " @ " + outlet.getName(), null,
                describe(m), request.note(), outlet.getId());
        return Views.of(level);
    }

    @Transactional
    public List<Views.StockRow> transfer(Dtos.TransferRequest request) {
        access.require(Permission.STOCK_TRANSFER);
        Long tenantId = access.tenantId();
        if (request.fromOutletId().equals(request.toOutletId())) {
            throw ApiException.badRequest("Choose two different outlets");
        }
        Outlet from = access.outlet(request.fromOutletId());
        Outlet to = outletRepository.findByIdAndTenant(request.toOutletId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("Destination outlet not found"));
        ProductVariant variant = trackedVariant(request.variantId(), tenantId);
        int upp = unitsPer(variant, request.unit(), null);
        long base = Math.multiplyExact(request.quantity(), (long) upp);
        String reference = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Lock in a fixed order so two opposite transfers cannot deadlock.
        StockLevel source;
        StockLevel target;
        if (from.getId() < to.getId()) {
            source = lockLevel(tenantId, from, variant);
            target = lockLevel(tenantId, to, variant);
        } else {
            target = lockLevel(tenantId, to, variant);
            source = lockLevel(tenantId, from, variant);
        }
        BigDecimal cost = source.getAvgUnitCost();
        StockMovement out = apply(source, from, variant, MovementType.TRANSFER_OUT, request.quantity(), request.unit(),
                upp, -base, cost, request.note(), reference, false);
        StockMovement in = apply(target, to, variant, MovementType.TRANSFER_IN, request.quantity(), request.unit(),
                upp, base, cost, request.note(), reference, true);
        audit.log("stock.transferred", "StockMovement", out.getId(), reference, describe(out), describe(in),
                request.note(), from.getId());
        return List.of(Views.of(source), Views.of(target));
    }

    @Transactional
    public Views.StockRow adjust(Dtos.AdjustRequest request) {
        access.require(Permission.STOCK_ADJUST);
        Long tenantId = access.tenantId();
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("A written reason is required");
        }
        Outlet outlet = access.outlet(request.outletId());
        ProductVariant variant = trackedVariant(request.variantId(), tenantId);
        int upp = unitsPer(variant, request.unit(), null);
        long entered = Math.multiplyExact(request.quantity(), (long) upp);
        StockLevel level = lockLevel(tenantId, outlet, variant);
        long before = level.getQuantity();

        long delta = switch (request.type()) {
            case BREAKAGE, WASTE -> -entered;
            case RETURN -> entered;
            case CORRECTION -> entered - before;
            default -> throw ApiException.badRequest("Use Receive, Transfer or an order for this kind of movement");
        };
        if (request.type() != MovementType.CORRECTION && request.quantity() == 0) {
            throw ApiException.badRequest("Quantity must be more than zero");
        }
        StockMovement m = apply(level, outlet, variant, request.type(), request.quantity(), request.unit(), upp, delta,
                level.getAvgUnitCost(), request.reason(), null, false);
        audit.log("stock." + request.type().name().toLowerCase(), "StockMovement", m.getId(),
                variant.displayName() + " @ " + outlet.getName(), before, level.getQuantity(), request.reason(),
                outlet.getId());
        return Views.of(level);
    }

    // ---------- used by orders ----------

    /** Result of taking stock off the shelf for a sale. */
    public record Sold(BigDecimal totalCost, BigDecimal unitCost) {}

    /**
     * Deducts stock for a delivered order line. Refuses (and so rolls the delivery back) when there is not enough.
     */
    @Transactional
    public Sold sell(Outlet outlet, ProductVariant variant, UnitType unit, int quantity, int unitsPerPack,
                     long baseQuantity, String orderCode) {
        Long tenantId = outlet.getTenantId();
        StockLevel level = lockLevel(tenantId, outlet, variant);
        BigDecimal cost = level.getAvgUnitCost();
        apply(level, outlet, variant, MovementType.SALE, quantity, unit, unitsPerPack, -baseQuantity, cost, null,
                orderCode, false);
        return new Sold(cost.multiply(BigDecimal.valueOf(baseQuantity)).setScale(2, RoundingMode.HALF_UP), cost);
    }

    public long available(Outlet outlet, ProductVariant variant) {
        return levelRepository.find(outlet.getTenantId(), outlet.getId(), variant.getId())
                .map(StockLevel::getQuantity).orElse(0L);
    }

    // ---------- internals ----------

    private ProductVariant trackedVariant(Long id, Long tenantId) {
        ProductVariant variant = variantRepository.findByIdAndTenant(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Menu item not found"));
        if (!variant.isTracked()) {
            throw ApiException.badRequest(variant.displayName() + " is not counted in stock");
        }
        return variant;
    }

    private StockLevel lockLevel(Long tenantId, Outlet outlet, ProductVariant variant) {
        return levelRepository.findForUpdate(tenantId, outlet.getId(), variant.getId()).orElseGet(() -> {
            StockLevel created = new StockLevel();
            created.setTenantId(tenantId);
            created.setOutlet(outlet);
            created.setVariant(variant);
            levelRepository.saveAndFlush(created);
            return levelRepository.findForUpdate(tenantId, outlet.getId(), variant.getId()).orElse(created);
        });
    }

    private StockMovement apply(StockLevel level, Outlet outlet, ProductVariant variant, MovementType type,
                                long enteredQuantity, UnitType unit, int unitsPerPack, long baseDelta,
                                BigDecimal unitCost, String reason, String reference, boolean costIn) {
        long newQuantity = level.getQuantity() + baseDelta;
        if (newQuantity < 0 && !allowsNegative(outlet.getTenantId())) {
            throw ApiException.conflict("Not enough stock of " + variant.displayName() + " at " + outlet.getName()
                    + ": " + Views.displayQuantity(level.getQuantity(), variant) + " available, "
                    + Views.displayQuantity(-baseDelta, variant) + " needed");
        }
        if (costIn && baseDelta > 0 && unitCost != null) {
            long existing = Math.max(0, level.getQuantity());
            BigDecimal total = level.getAvgUnitCost().multiply(BigDecimal.valueOf(existing))
                    .add(unitCost.multiply(BigDecimal.valueOf(baseDelta)));
            level.setAvgUnitCost(total.divide(BigDecimal.valueOf(existing + baseDelta), 4, RoundingMode.HALF_UP));
        }
        level.setQuantity(newQuantity);
        levelRepository.save(level);

        User actor = access.principal();
        StockMovement m = new StockMovement();
        m.setTenantId(outlet.getTenantId());
        m.setOutlet(outlet);
        m.setVariant(variant);
        m.setType(type);
        m.setEnteredQuantity(enteredQuantity);
        m.setEnteredUnit(unit);
        m.setUnitsPerPackUsed(unitsPerPack);
        m.setBaseQuantity(baseDelta);
        m.setBalanceAfter(newQuantity);
        m.setUnitCost(unitCost);
        m.setReason(reason);
        m.setReference(reference);
        if (actor != null) {
            m.setCreatedById(actor.getId());
            m.setCreatedByName(actor.getFullName());
        }
        return movementRepository.save(m);
    }

    private boolean allowsNegative(Long tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::isAllowNegativeStock).orElse(false);
    }

    private Object describe(StockMovement m) {
        return java.util.Map.of("type", m.getType().name(), "entered", m.getEnteredQuantity() + " " + m.getEnteredUnit(),
                "unitsPerPack", m.getUnitsPerPackUsed(), "baseQuantity", m.getBaseQuantity(),
                "balanceAfter", m.getBalanceAfter());
    }
}
