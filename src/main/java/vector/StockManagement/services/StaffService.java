package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Outlet;
import vector.StockManagement.model.Tenant;
import vector.StockManagement.model.User;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.Gender;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.model.enums.Role;
import vector.StockManagement.model.enums.UserStatus;
import vector.StockManagement.repositories.OutletRepository;
import vector.StockManagement.repositories.TenantRepository;
import vector.StockManagement.repositories.TokenRepository;
import vector.StockManagement.repositories.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StaffService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OutletRepository outletRepository;
    private final TenantRepository tenantRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Access access;
    private final AuditService audit;

    /** Returned once so the creator can pass the password on. */
    public record StaffCreated(Views.StaffView staff, String generatedPassword) {}

    public static String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }

    public static Views.StaffView view(User u) {
        return new Views.StaffView(u.getId(), u.getFirstName(), u.getLastName(), u.getFullName(), u.getEmail(),
                u.getPhone(), u.getRole(), u.getUserStatus(),
                u.getOutlets().stream().map(Outlet::getId).sorted().toList(),
                u.getOutlets().stream().map(Outlet::getName).sorted().toList());
    }

    @Transactional(readOnly = true)
    public List<Views.StaffView> list() {
        access.require(Permission.STAFF_MANAGE);
        return userRepository.findAllByTenantId(access.tenantId()).stream().map(StaffService::view).toList();
    }

    @Transactional
    public StaffCreated create(Dtos.StaffRequest request) {
        access.require(Permission.STAFF_MANAGE);
        User actor = access.user();
        if (!actor.getRole().assignableRoles().contains(request.role())) {
            throw ApiException.forbidden("You cannot create a " + request.role().name().toLowerCase().replace('_', ' '));
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict("Email already in use");
        }
        Set<Outlet> outlets = resolveOutlets(request.outletIds());
        requireOutletsForRole(request.role(), outlets);

        String password = request.password();
        String generated = null;
        if (password == null || password.isBlank()) {
            password = generated = generatePassword();
        } else if (password.length() < 8) {
            throw ApiException.badRequest("Password must be at least 8 characters");
        }
        Tenant tenant = tenantRepository.findById(access.tenantId()).orElseThrow();
        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phone(request.phone())
                .gender(parseGender(request.gender()))
                .password(passwordEncoder.encode(password))
                .role(request.role())
                .userStatus(UserStatus.ACTIVE)
                .tenant(tenant)
                .outlets(outlets)
                .createdAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);
        audit.log("staff.created", "User", user.getId(), email, null, view(user), null, null);
        return new StaffCreated(view(user), generated);
    }

    @Transactional
    public Views.StaffView update(Long id, Dtos.StaffUpdateRequest request) {
        access.require(Permission.STAFF_MANAGE);
        User actor = access.user();
        User target = userRepository.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Staff member not found"));
        if (target.getRole() == Role.OWNER) throw ApiException.forbidden("The owner account cannot be changed here");
        if (!actor.getRole().assignableRoles().contains(target.getRole())) {
            throw ApiException.forbidden("You cannot change this person");
        }
        Views.StaffView before = view(target);
        if (request.firstName() != null && !request.firstName().isBlank()) target.setFirstName(request.firstName().trim());
        if (request.lastName() != null && !request.lastName().isBlank()) target.setLastName(request.lastName().trim());
        if (request.phone() != null) target.setPhone(request.phone());
        if (request.role() != null && request.role() != target.getRole()) {
            if (!actor.getRole().assignableRoles().contains(request.role())) {
                throw ApiException.forbidden("You cannot give this role");
            }
            target.setRole(request.role());
        }
        if (request.outletIds() != null) {
            target.setOutlets(resolveOutlets(request.outletIds()));
        }
        requireOutletsForRole(target.getRole(), target.getOutlets());
        if (request.status() != null) {
            target.setUserStatus(request.status());
            if (request.status() != UserStatus.ACTIVE) revokeTokens(target);
        }
        userRepository.save(target);
        audit.log("staff.updated", "User", id, target.getEmail(), before, view(target), null, null);
        return view(target);
    }

    @Transactional
    public StaffCreated resetPassword(Long id) {
        access.require(Permission.STAFF_MANAGE);
        User actor = access.user();
        User target = userRepository.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Staff member not found"));
        if (!actor.getRole().assignableRoles().contains(target.getRole())) {
            throw ApiException.forbidden("You cannot reset this person's password");
        }
        String password = generatePassword();
        target.setPassword(passwordEncoder.encode(password));
        userRepository.save(target);
        revokeTokens(target);
        audit.log("staff.password_reset", "User", id, target.getEmail(), null, null, null, null);
        return new StaffCreated(view(target), password);
    }

    private void revokeTokens(User user) {
        var tokens = tokenRepository.findAllValidTokenByUser(user.getId());
        tokens.forEach(t -> {
            t.setRevoked(true);
            t.setExpired(true);
        });
        tokenRepository.saveAll(tokens);
    }

    private Set<Outlet> resolveOutlets(List<Long> ids) {
        Set<Outlet> outlets = new HashSet<>();
        if (ids == null) return outlets;
        Long tenantId = access.tenantId();
        for (Long id : ids) {
            outlets.add(outletRepository.findByIdAndTenant(id, tenantId)
                    .orElseThrow(() -> ApiException.notFound("Outlet not found")));
        }
        return outlets;
    }

    private void requireOutletsForRole(Role role, Set<Outlet> outlets) {
        if ((role == Role.MANAGER || role == Role.WAITER) && outlets.isEmpty()) {
            throw ApiException.badRequest("Choose at least one outlet for this person");
        }
    }

    private Gender parseGender(String gender) {
        if (gender == null || gender.isBlank()) return null;
        try {
            return Gender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
