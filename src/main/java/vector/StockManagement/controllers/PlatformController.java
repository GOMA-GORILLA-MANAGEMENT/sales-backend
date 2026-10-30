package vector.StockManagement.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.services.PlatformService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platform;

    @GetMapping("/overview")
    public PlatformService.Overview overview() {
        return platform.overview();
    }

    @GetMapping("/restaurants")
    public List<PlatformService.RestaurantRow> list() {
        return platform.list();
    }

    @GetMapping("/restaurants/{id}")
    public PlatformService.RestaurantDetail detail(@PathVariable Long id) {
        return platform.detail(id);
    }

    @PostMapping("/restaurants")
    public ResponseEntity<PlatformService.Onboarded> onboard(@Valid @RequestBody Dtos.OnboardRequest request) {
        return ResponseEntity.status(201).body(platform.onboard(request));
    }

    @PutMapping("/restaurants/{id}")
    public PlatformService.RestaurantRow update(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateRestaurantRequest body) {
        return platform.update(id, body);
    }

    @PostMapping("/restaurants/{id}/suspend")
    public PlatformService.RestaurantRow suspend(@PathVariable Long id, @RequestBody(required = false) Dtos.ReasonRequest body) {
        return platform.setActive(id, false, body == null ? null : body.reason());
    }

    @PostMapping("/restaurants/{id}/reactivate")
    public PlatformService.RestaurantRow reactivate(@PathVariable Long id) {
        return platform.setActive(id, true, null);
    }

    @PostMapping("/restaurants/{id}/reset-owner-password")
    public PlatformService.PasswordReset resetOwner(@PathVariable Long id,
                                                    @RequestBody(required = false) Dtos.ResetPasswordForOwnerRequest body) {
        return platform.resetOwnerPassword(id, body == null ? null : body.password());
    }
}
