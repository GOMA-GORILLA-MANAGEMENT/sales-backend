package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Product;
import vector.StockManagement.model.ProductVariant;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.repositories.ProductRepository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final Access access;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<Views.ProductView> list() {
        access.require(Permission.MENU_VIEW);
        return productRepository.findAllByTenant(access.tenantId()).stream().map(Views::of).toList();
    }

    @Transactional
    public Views.ProductView create(Dtos.ProductRequest request) {
        access.require(Permission.MENU_MANAGE);
        Product product = new Product();
        product.setTenantId(access.tenantId());
        applyProduct(product, request);
        product = productRepository.save(product);
        audit.log("menu.item_created", "Product", product.getId(), product.getName(), null, Views.of(product), null, null);
        return Views.of(product);
    }

    @Transactional
    public Views.ProductView update(Long id, Dtos.ProductRequest request) {
        access.require(Permission.MENU_MANAGE);
        Product product = productRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Menu item not found"));
        Views.ProductView before = Views.of(product);
        applyProduct(product, request);
        productRepository.save(product);
        audit.log("menu.item_updated", "Product", id, product.getName(), before, Views.of(product), null, null);
        return Views.of(product);
    }

    private void applyProduct(Product product, Dtos.ProductRequest request) {
        List<Dtos.VariantRequest> variants = request.variants();
        if (request.hasSizes()) {
            if (variants.size() < 2) throw ApiException.badRequest("A product with sizes needs at least two sizes");
            Set<String> labels = new HashSet<>();
            for (Dtos.VariantRequest v : variants) {
                if (v.label() == null || v.label().isBlank()) throw ApiException.badRequest("Every size needs a name");
                if (!labels.add(v.label().trim().toLowerCase())) throw ApiException.badRequest("Size names must be different");
            }
        } else if (variants.size() != 1) {
            throw ApiException.badRequest("A product without sizes has exactly one price and unit setup");
        }
        product.setName(request.name().trim());
        product.setCategory(request.category());
        product.setDescription(request.description());
        product.setHasSizes(request.hasSizes());
        product.setActive(request.active() == null || request.active());

        Set<Long> kept = new HashSet<>();
        for (Dtos.VariantRequest r : variants) {
            ProductVariant variant = null;
            if (r.id() != null) {
                variant = product.getVariants().stream().filter(v -> v.getId().equals(r.id())).findFirst()
                        .orElseThrow(() -> ApiException.badRequest("Unknown size on this item"));
            }
            if (variant == null) {
                variant = new ProductVariant();
                variant.setTenantId(access.tenantId());
                variant.setProduct(product);
                product.getVariants().add(variant);
            }
            applyVariant(variant, r, request.hasSizes());
            if (variant.getId() != null) kept.add(variant.getId());
        }
        // Sizes that were taken out are switched off, never deleted, because orders and stock refer to them.
        for (ProductVariant v : product.getVariants()) {
            if (v.getId() != null && !kept.contains(v.getId())) v.setActive(false);
        }
    }

    private void applyVariant(ProductVariant v, Dtos.VariantRequest r, boolean hasSizes) {
        v.setLabel(hasSizes ? r.label().trim() : "Standard");
        v.setSellPrice(r.sellPrice());
        v.setBaseUnitName(r.baseUnitName() == null || r.baseUnitName().isBlank() ? "piece" : r.baseUnitName().trim());
        String pack = r.packUnitName() == null || r.packUnitName().isBlank() ? null : r.packUnitName().trim();
        Integer upp = r.unitsPerPack();
        if (pack != null && (upp == null || upp < 2)) {
            throw ApiException.badRequest("Say how many " + v.getBaseUnitName() + "s are in one " + pack);
        }
        v.setPackUnitName(pack);
        v.setUnitsPerPack(pack == null ? null : upp);
        BigDecimal packPrice = pack == null ? null : r.packPrice();
        v.setPackPrice(packPrice);
        v.setTracked(r.tracked() == null || r.tracked());
        v.setLowStockThreshold(r.lowStockThreshold() == null ? 0L : r.lowStockThreshold());
        v.setActive(r.active() == null || r.active());
    }
}
