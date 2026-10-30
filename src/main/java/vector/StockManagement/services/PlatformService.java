package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.config.TenantContext;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Outlet;
import vector.StockManagement.model.Tenant;
import vector.StockManagement.model.User;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.*;
import vector.StockManagement.repositories.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** The platform console: only the super admin creates, suspends and supports restaurant accounts. */
@Service
@RequiredArgsConstructor
public class PlatformService {

    private final TenantRepository tenantRepository;
    private final OutletRepository outletRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Access access;

    public record Overview(long restaurants, long active, long suspended, long outlets, long staff, BigDecimal revenue) {}

    public record RestaurantRow(Long id, String name, String code, boolean active, long outlets, long staff,
                                BigDecimal revenue, String ownerName, String ownerEmail, LocalDateTime createdAt,
                                String phone, String address, String ownerFirstName, String ownerLastName,
                                String ownerPhone) {}

    public record RestaurantDetail(RestaurantRow summary, List<Views.StaffView> staff, List<Views.OutletView> outlets,
                                   List<Map<String, Object>> roles, boolean approvalRequired) {}

    public record Onboarded(RestaurantRow restaurant, String ownerEmail, String generatedPassword) {}

    public record PasswordReset(String ownerEmail, String generatedPassword) {}

    private void requireSuperAdmin() {
        access.require(Permission.PLATFORM_MANAGE);
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        requireSuperAdmin();
        return new Overview(tenantRepository.count(), tenantRepository.countByActiveTrue(), tenantRepository.countByActiveFalse(),
                outletRepository.count(), userRepository.countByRoleNot(Role.SUPER_ADMIN), orderRepository.platformRevenue());
    }

    @Transactional(readOnly = true)
    public List<RestaurantRow> list() {
        requireSuperAdmin();
        return tenantRepository.findAll().stream()
                .filter(t -> !"PLATFORM".equals(t.getCode()))
                .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::row).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantDetail detail(Long id) {
        requireSuperAdmin();
        Tenant tenant = find(id);
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Role role : Role.values()) {
            if (role == Role.SUPER_ADMIN) continue;
            roles.add(Map.of("role", role.name(), "permissions",
                    role.getPermissions().stream().map(Permission::getPermission).sorted().toList()));
        }
        return new RestaurantDetail(row(tenant),
                userRepository.findAllByTenantId(id).stream().map(StaffService::view).toList(),
                outletRepository.findAllByTenant(id).stream().map(Views::of).toList(), roles, tenant.isApprovalRequired());
    }

    /**
     * Creates the account, its first outlet and the owner login in one transaction.
     * The generated password is returned once.
     */
    @Transactional
    public Onboarded onboard(Dtos.OnboardRequest request) {
        requireSuperAdmin();
        String name = request.businessName().trim();
        if (tenantRepository.existsByNameIgnoreCase(name)) throw ApiException.conflict("A business with this name already exists");
        String email = request.ownerEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) throw ApiException.conflict("This email is already in use");

        String password = request.password();
        String generated = null;
        if (password == null || password.isBlank()) {
            password = generated = StaffService.generatePassword();
        } else if (password.length() < 8) {
            throw ApiException.badRequest("Password must be at least 8 characters");
        }

        Tenant tenant = new Tenant(name, "R" + String.format("%05d", tenantRepository.count() + 1));
        tenant.setEmail(email);
        tenant.setPhone(request.ownerPhone());
        tenant = tenantRepository.saveAndFlush(tenant);

        Outlet outlet = new Outlet();
        outlet.setTenantId(tenant.getId());
        outlet.setName(request.outletName().trim());
        outlet.setType(request.outletType() == null ? OutletType.RESTAURANT : request.outletType());
        outlet = outletRepository.save(outlet);

        User owner = User.builder()
                .firstName(request.ownerFirstName().trim())
                .lastName(request.ownerLastName().trim())
                .email(email)
                .phone(request.ownerPhone())
                .password(passwordEncoder.encode(password))
                .role(Role.OWNER)
                .userStatus(UserStatus.ACTIVE)
                .tenant(tenant)
                .outlets(new HashSet<>(Set.of(outlet)))
                .createdAt(LocalDateTime.now())
                .build();
        owner = userRepository.save(owner);

        Map<String, Object> snapshot = Map.of("business", name, "firstOutlet", outlet.getName(), "owner", email);
        audit.logIn(0L, "platform.restaurant_onboarded", "Tenant", tenant.getId(), name, null, snapshot, null, null);
        audit.logIn(tenant.getId(), "business.onboarded", "Tenant", tenant.getId(), name, null, snapshot, null, outlet.getId());
        return new Onboarded(row(tenant), email, generated);
    }

    /** Edits a restaurant's details and its owner's contact details. */
    @Transactional
    public RestaurantRow update(Long id, Dtos.UpdateRestaurantRequest r) {
        requireSuperAdmin();
        Tenant tenant = find(id);
        String name = r.name().trim();
        if (!name.equalsIgnoreCase(tenant.getName()) && tenantRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A business with this name already exists");
        }
        RestaurantRow before = row(tenant);
        tenant.setName(name);
        tenant.setPhone(r.phone());
        tenant.setAddress(r.address());
        tenantRepository.save(tenant);

        List<User> owners = userRepository.findByTenantIdAndRole(id, Role.OWNER);
        if (!owners.isEmpty()) {
            User owner = owners.get(0);
            if (r.ownerFirstName() != null && !r.ownerFirstName().isBlank()) owner.setFirstName(r.ownerFirstName().trim());
            if (r.ownerLastName() != null && !r.ownerLastName().isBlank()) owner.setLastName(r.ownerLastName().trim());
            owner.setPhone(r.ownerPhone());
            if (r.ownerEmail() != null && !r.ownerEmail().isBlank()) {
                String email = r.ownerEmail().trim().toLowerCase();
                if (!email.equals(owner.getEmail())) {
                    if (userRepository.findByEmail(email).isPresent()) throw ApiException.conflict("This email is already in use");
                    owner.setEmail(email);
                    // The login name changed, so any open session ends.
                    var tokens = tokenRepository.findAllValidTokenByUser(owner.getId());
                    tokens.forEach(t -> { t.setRevoked(true); t.setExpired(true); });
                    tokenRepository.saveAll(tokens);
                }
            }
            userRepository.save(owner);
        }
        RestaurantRow after = row(tenant);
        audit.logIn(0L, "platform.restaurant_updated", "Tenant", id, tenant.getName(), before, after, null, null);
        audit.logIn(id, "business.updated_by_platform", "Tenant", id, tenant.getName(), before, after, null, null);
        return after;
    }

    @Transactional
    public RestaurantRow setActive(Long id, boolean active, String reason) {
        requireSuperAdmin();
        Tenant tenant = find(id);
        boolean before = tenant.isActive();
        tenant.setActive(active);
        tenantRepository.save(tenant);
        String action = active ? "platform.restaurant_reactivated" : "platform.restaurant_suspended";
        audit.logIn(0L, action, "Tenant", id, tenant.getName(), before, active, reason, null);
        audit.logIn(id, active ? "business.reactivated" : "business.suspended", "Tenant", id, tenant.getName(), before,
                active, reason, null);
        return row(tenant);
    }

    @Transactional
    public PasswordReset resetOwnerPassword(Long id, String requested) {
        requireSuperAdmin();
        Tenant tenant = find(id);
        List<User> owners = userRepository.findByTenantIdAndRole(id, Role.OWNER);
        if (owners.isEmpty()) throw ApiException.notFound("This business has no owner");
        User owner = owners.get(0);
        String password = requested;
        String generated = null;
        if (password == null || password.isBlank()) {
            password = generated = StaffService.generatePassword();
        } else if (password.length() < 8) {
            throw ApiException.badRequest("Password must be at least 8 characters");
        }
        owner.setPassword(passwordEncoder.encode(password));
        userRepository.save(owner);
        var tokens = tokenRepository.findAllValidTokenByUser(owner.getId());
        tokens.forEach(t -> {
            t.setRevoked(true);
            t.setExpired(true);
        });
        tokenRepository.saveAll(tokens);
        audit.logIn(0L, "platform.owner_password_reset", "User", owner.getId(), tenant.getName(), null, null, null, null);
        audit.logIn(id, "business.owner_password_reset", "User", owner.getId(), owner.getEmail(), null, null, null, null);
        return new PasswordReset(owner.getEmail(), generated);
    }

    @Transactional
    public RestaurantRow updateSettings(Dtos.SettingsRequest request) {
        access.require(Permission.SETTINGS_MANAGE);
        Tenant tenant = find(access.tenantId());
        Map<String, Object> before = Map.of("approvalRequired", tenant.isApprovalRequired(),
                "allowNegativeStock", tenant.isAllowNegativeStock(), "name", tenant.getName());
        if (request.approvalRequired() != null) tenant.setApprovalRequired(request.approvalRequired());
        if (request.allowNegativeStock() != null) tenant.setAllowNegativeStock(request.allowNegativeStock());
        if (request.name() != null && !request.name().isBlank()) tenant.setName(request.name().trim());
        tenantRepository.save(tenant);
        audit.log("settings.updated", "Tenant", tenant.getId(), tenant.getName(), before,
                Map.of("approvalRequired", tenant.isApprovalRequired(), "allowNegativeStock", tenant.isAllowNegativeStock(),
                        "name", tenant.getName()), null, null);
        return row(tenant);
    }

    private Tenant find(Long id) {
        return tenantRepository.findById(id).orElseThrow(() -> ApiException.notFound("Business not found"));
    }

    private RestaurantRow row(Tenant t) {
        List<User> owners = userRepository.findByTenantIdAndRole(t.getId(), Role.OWNER);
        User owner = owners.isEmpty() ? null : owners.get(0);
        return new RestaurantRow(t.getId(), t.getName(), t.getCode(), t.isActive(), outletRepository.countByTenant(t.getId()),
                userRepository.countByTenantId(t.getId()), orderRepository.totalRevenue(t.getId()),
                owner == null ? null : owner.getFullName(), owner == null ? null : owner.getEmail(), t.getCreatedAt(),
                t.getPhone(), t.getAddress(), owner == null ? null : owner.getFirstName(),
                owner == null ? null : owner.getLastName(), owner == null ? null : owner.getPhone());
    }
}
