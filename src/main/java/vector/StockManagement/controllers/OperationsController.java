package vector.StockManagement.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vector.StockManagement.model.AuditLog;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.OrderStatus;
import vector.StockManagement.services.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Orders, stock, debts, dashboard, reports and the audit trail. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OperationsController {

    private final OrderService orders;
    private final StockService stock;
    private final DebtService debts;
    private final DashboardService dashboard;
    private final ReportService reports;
    private final ExportService exports;
    private final AuditService audit;
    private final ExpenseService expenses;

    // ----- orders -----

    @GetMapping("/orders")
    public List<Views.OrderView> orders(@RequestParam(required = false) Long outletId,
                                        @RequestParam(required = false) OrderStatus status) {
        return orders.list(outletId, status);
    }

    @GetMapping("/orders/{id}")
    public Views.OrderView order(@PathVariable Long id) { return orders.get(id); }

    @PostMapping("/orders")
    public ResponseEntity<Views.OrderView> createOrder(@Valid @RequestBody Dtos.OrderRequest r) {
        return ResponseEntity.status(201).body(orders.create(r));
    }

    @PostMapping("/orders/{id}/approve")
    public Views.OrderView approve(@PathVariable Long id) { return orders.approve(id); }

    @PostMapping("/orders/{id}/reject")
    public Views.OrderView reject(@PathVariable Long id, @RequestBody Dtos.ReasonRequest r) { return orders.reject(id, r.reason()); }

    @PostMapping("/orders/{id}/cancel")
    public Views.OrderView cancel(@PathVariable Long id, @RequestBody Dtos.ReasonRequest r) { return orders.cancel(id, r.reason()); }

    @PostMapping("/orders/{id}/deliver")
    public Views.OrderView deliver(@PathVariable Long id, @Valid @RequestBody Dtos.DeliverRequest r) {
        return orders.deliver(id, r);
    }

    // ----- stock -----

    @GetMapping("/stock")
    public List<Views.StockRow> stock(@RequestParam(required = false) Long outletId,
                                      @RequestParam(defaultValue = "false") boolean lowOnly) {
        return stock.levels(outletId, lowOnly);
    }

    @GetMapping("/stock/movements")
    public List<Views.MovementView> movements(@RequestParam(required = false) Long outletId,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return stock.movements(outletId, Api.date(from), Api.date(to));
    }

    @PostMapping("/stock/receive")
    public Views.StockRow receive(@Valid @RequestBody Dtos.ReceiveStockRequest r) { return stock.receive(r); }

    @PostMapping("/stock/transfer")
    public List<Views.StockRow> transfer(@Valid @RequestBody Dtos.TransferRequest r) { return stock.transfer(r); }

    @PostMapping("/stock/adjust")
    public Views.StockRow adjust(@Valid @RequestBody Dtos.AdjustRequest r) { return stock.adjust(r); }

    // ----- debts -----

    @GetMapping("/debts")
    public List<Views.DebtView> debts(@RequestParam(required = false) Long outletId,
                                      @RequestParam(required = false) Long customerId,
                                      @RequestParam(defaultValue = "true") boolean openOnly) {
        return debts.list(outletId, customerId, openOnly);
    }

    @GetMapping("/debts/{id}")
    public Views.DebtDetail debt(@PathVariable Long id) { return debts.detail(id); }

    @PostMapping("/debts/{id}/payments")
    public Views.DebtDetail pay(@PathVariable Long id, @Valid @RequestBody Dtos.DebtPaymentRequest r) { return debts.pay(id, r); }

    @PostMapping("/debts/{id}/adjustments")
    public Views.DebtDetail adjustDebt(@PathVariable Long id, @Valid @RequestBody Dtos.DebtAdjustRequest r) {
        return debts.adjust(id, r);
    }

    @PostMapping("/payments/{id}/reverse")
    public Views.DebtDetail reverse(@PathVariable Long id, @RequestBody Dtos.ReasonRequest r) {
        return debts.reversePayment(id, r.reason());
    }

    @GetMapping("/customers/{id}/statement")
    public DebtService.Statement statement(@PathVariable Long id) { return debts.statement(id); }

    // ----- expenses -----

    @GetMapping("/expenses")
    public List<Views.ExpenseView> expenses(@RequestParam(required = false) Long outletId, @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        return expenses.list(outletId, Api.date(from), Api.date(to));
    }

    @PostMapping("/expenses")
    public ResponseEntity<Views.ExpenseView> createExpense(@Valid @RequestBody Dtos.ExpenseRequest r) {
        return ResponseEntity.status(201).body(expenses.create(r));
    }

    @PostMapping("/expenses/{id}/void")
    public Views.ExpenseView voidExpense(@PathVariable Long id, @RequestBody Dtos.ReasonRequest r) {
        return expenses.voidExpense(id, r.reason());
    }

    // ----- dashboard and reports -----

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                         @RequestParam(required = false) Long outletId) {
        return dashboard.dashboard(Api.date(from), Api.date(to), outletId);
    }

    @GetMapping("/reports/{type}")
    public Views.ReportTable report(@PathVariable String type, @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to, @RequestParam(required = false) Long outletId) {
        return reports.build(type, Api.date(from), Api.date(to), outletId);
    }

    @GetMapping("/reports/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to, @RequestParam(required = false) Long outletId) {
        Views.ReportTable table = reports.build(type, Api.date(from), Api.date(to), outletId);
        byte[] bytes = exports.toXlsx(table);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(type + "-report.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ----- audit -----

    @GetMapping("/audit")
    public List<Views.AuditView> auditTrail(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                            @RequestParam(required = false) String action) {
        List<AuditLog> entries = audit.search(Api.date(from), Api.date(to), action);
        return entries.stream().map(Views::of).toList();
    }

    @GetMapping("/audit/verify")
    public AuditService.Verification verify() { return audit.verifyCurrentBusiness(); }
}
