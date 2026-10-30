package vector.StockManagement.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Customer;
import vector.StockManagement.model.dto.Dtos;
import vector.StockManagement.model.dto.Views;
import vector.StockManagement.model.enums.Permission;
import vector.StockManagement.repositories.CustomerRepository;
import vector.StockManagement.repositories.DebtRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final DebtRepository debtRepository;
    private final Access access;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<Views.CustomerView> list() {
        access.require(Permission.CUSTOMER_VIEW);
        Long tenantId = access.tenantId();
        Map<Long, BigDecimal> owed = new HashMap<>();
        for (Object[] row : debtRepository.outstandingByCustomer(tenantId)) {
            owed.put((Long) row[0], (BigDecimal) row[1]);
        }
        return customerRepository.findAllByTenant(tenantId).stream()
                .map(c -> Views.of(c, owed.getOrDefault(c.getId(), BigDecimal.ZERO))).toList();
    }

    @Transactional
    public Views.CustomerView create(Dtos.CustomerRequest request) {
        access.require(Permission.CUSTOMER_MANAGE);
        Customer customer = new Customer();
        customer.setTenantId(access.tenantId());
        apply(customer, request);
        customer = customerRepository.save(customer);
        audit.log("customer.created", "Customer", customer.getId(), customer.getName(), null,
                Views.of(customer, BigDecimal.ZERO), null, null);
        return Views.of(customer, BigDecimal.ZERO);
    }

    /**
     * Used while taking an order: finds the customer by phone or name, or adds a new one with no credit.
     * Both fields are optional, but at least one is needed.
     */
    @Transactional
    public Views.CustomerView findOrCreate(Dtos.QuickCustomerRequest request) {
        if (!access.can(Permission.CUSTOMER_MANAGE)) access.require(Permission.ORDER_CREATE);
        Long tenantId = access.tenantId();
        String name = request.name() == null ? "" : request.name().trim();
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (name.isEmpty() && phone.isEmpty()) throw ApiException.badRequest("Enter the customer's name or phone number");
        if (!phone.isEmpty()) {
            var byPhone = customerRepository.findByPhone(tenantId, phone);
            if (!byPhone.isEmpty()) return Views.of(byPhone.get(0), debtRepository.outstandingForCustomer(tenantId, byPhone.get(0).getId()));
        } else {
            var byName = customerRepository.findByNameIgnoreCase(tenantId, name);
            if (!byName.isEmpty()) return Views.of(byName.get(0), debtRepository.outstandingForCustomer(tenantId, byName.get(0).getId()));
        }
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName(name.isEmpty() ? phone : name);
        customer.setPhone(phone.isEmpty() ? null : phone);
        customer = customerRepository.save(customer);
        audit.log("customer.created", "Customer", customer.getId(), customer.getName(), null,
                Views.of(customer, BigDecimal.ZERO), "Added while taking an order", null);
        return Views.of(customer, BigDecimal.ZERO);
    }

    @Transactional
    public Views.CustomerView update(Long id, Dtos.CustomerRequest request) {
        access.require(Permission.CUSTOMER_MANAGE);
        Customer customer = customerRepository.findByIdAndTenant(id, access.tenantId())
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        BigDecimal owed = debtRepository.outstandingForCustomer(access.tenantId(), id);
        Views.CustomerView before = Views.of(customer, owed);
        apply(customer, request);
        audit.log("customer.updated", "Customer", id, customer.getName(), before, Views.of(customer, owed), null, null);
        return Views.of(customer, owed);
    }

    private void apply(Customer customer, Dtos.CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());
        customer.setIdNumber(request.idNumber());
        if (request.active() != null) customer.setActive(request.active());
        // Credit terms are a money decision: only owners and overall managers set them.
        if (access.can(Permission.CREDIT_OVERRIDE)) {
            if (request.creditLimit() != null) customer.setCreditLimit(request.creditLimit());
            if (request.creditDays() != null) customer.setCreditDays(request.creditDays());
        }
    }
}
