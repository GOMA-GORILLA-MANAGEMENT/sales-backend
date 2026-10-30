package vector.StockManagement.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.OutletStatus;
import vector.StockManagement.services.*;

import java.util.List;

/** Outlets, staff, menu, customers and business settings. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BusinessController {

    private final OutletService outlets;
    private final StaffService staff;
    private final CatalogService catalog;
    private final CustomerService customers;
    private final PlatformService platform;

    @GetMapping("/outlets")
    public List<Views.OutletView> outlets() { return outlets.list(); }

    @GetMapping("/outlets/transfer-targets")
    public List<Views.OutletView> transferTargets() { return outlets.transferTargets(); }

    @PostMapping("/outlets")
    public ResponseEntity<Views.OutletView> createOutlet(@Valid @RequestBody Dtos.OutletRequest r) {
        return ResponseEntity.status(201).body(outlets.create(r));
    }

    @PutMapping("/outlets/{id}")
    public Views.OutletView updateOutlet(@PathVariable Long id, @Valid @RequestBody Dtos.OutletRequest r) {
        return outlets.update(id, r);
    }

    @PutMapping("/outlets/{id}/status")
    public Views.OutletView outletStatus(@PathVariable Long id, @Valid @RequestBody Dtos.OutletStatusRequest r) {
        OutletStatus status = r.status();
        return outlets.setStatus(id, status);
    }

    @GetMapping("/staff")
    public List<Views.StaffView> staff() { return staff.list(); }

    @PostMapping("/staff")
    public ResponseEntity<StaffService.StaffCreated> createStaff(@Valid @RequestBody Dtos.StaffRequest r) {
        return ResponseEntity.status(201).body(staff.create(r));
    }

    @PutMapping("/staff/{id}")
    public Views.StaffView updateStaff(@PathVariable Long id, @RequestBody Dtos.StaffUpdateRequest r) {
        return staff.update(id, r);
    }

    @PostMapping("/staff/{id}/reset-password")
    public StaffService.StaffCreated resetStaffPassword(@PathVariable Long id) { return staff.resetPassword(id); }

    @GetMapping("/menu")
    public List<Views.ProductView> menu() { return catalog.list(); }

    @PostMapping("/menu")
    public ResponseEntity<Views.ProductView> createItem(@Valid @RequestBody Dtos.ProductRequest r) {
        return ResponseEntity.status(201).body(catalog.create(r));
    }

    @PutMapping("/menu/{id}")
    public Views.ProductView updateItem(@PathVariable Long id, @Valid @RequestBody Dtos.ProductRequest r) {
        return catalog.update(id, r);
    }

    @GetMapping("/customers")
    public List<Views.CustomerView> customers() { return customers.list(); }

    @PostMapping("/customers")
    public ResponseEntity<Views.CustomerView> createCustomer(@Valid @RequestBody Dtos.CustomerRequest r) {
        return ResponseEntity.status(201).body(customers.create(r));
    }

    @PostMapping("/customers/quick")
    public Views.CustomerView quickCustomer(@RequestBody Dtos.QuickCustomerRequest r) { return customers.findOrCreate(r); }

    @PutMapping("/customers/{id}")
    public Views.CustomerView updateCustomer(@PathVariable Long id, @Valid @RequestBody Dtos.CustomerRequest r) {
        return customers.update(id, r);
    }

    @PutMapping("/settings")
    public PlatformService.RestaurantRow settings(@RequestBody Dtos.SettingsRequest r) {
        return platform.updateSettings(r);
    }
}
