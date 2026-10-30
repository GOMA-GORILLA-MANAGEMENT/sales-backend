package vector.StockManagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BarRestaurantFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    static String admin, owner, ownerB, manager, manager2, waiter;
    static long tenantA, tenantB, outlet1, outlet2, outletB, fantaSmall, fantaLarge, dish, customer, tinyCustomer;
    static long orderId, debtId, paymentId;

    // ---------- helpers ----------

    JsonNode call(MockHttpServletRequestBuilder b, String token, Object body, int expected) throws Exception {
        b.contentType(MediaType.APPLICATION_JSON);
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.content(json.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andReturn();
        String text = r.getResponse().getContentAsString();
        assertEquals(expected, r.getResponse().getStatus(), "Unexpected status for " + b + ": " + text);
        return text.isBlank() ? json.createObjectNode() : json.readTree(text);
    }

    JsonNode POST(String url, String t, Object body, int status) throws Exception { return call(post(url), t, body, status); }
    JsonNode PUT(String url, String t, Object body, int status) throws Exception { return call(put(url), t, body, status); }
    JsonNode GET(String url, String t, int status) throws Exception { return call(get(url), t, null, status); }

    String login(String email, String password) throws Exception {
        return POST("/api/auth/login", null, Map.of("email", email, "password", password), 200).get("access_token").asText();
    }

    Map<String, Object> variant(String label, int price, Integer perCrate, boolean tracked) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("label", label);
        m.put("sellPrice", price);
        m.put("baseUnitName", "bottle");
        if (perCrate != null) { m.put("packUnitName", "crate"); m.put("unitsPerPack", perCrate); }
        m.put("tracked", tracked);
        m.put("lowStockThreshold", 5);
        return m;
    }

    // ---------- scenario ----------

    @Test @Order(1)
    void superAdminOnboardsTwoRestaurantsAndCannotSeeTheirData() throws Exception {
        admin = login("root@sales.test", "RootPass123");
        JsonNode a = POST("/api/admin/restaurants", admin, Map.of("businessName", "Gorilla Bar", "outletName", "Gorilla Main",
                "outletType", "BAR", "ownerFirstName", "Ada", "ownerLastName", "Owner", "ownerEmail", "ada@gorilla.test"), 201);
        JsonNode b = POST("/api/admin/restaurants", admin, Map.of("businessName", "Other Place", "outletName", "Other Main",
                "ownerFirstName", "Bo", "ownerLastName", "Owner", "ownerEmail", "bo@other.test", "password", "OtherPass123"), 201);
        tenantA = a.get("restaurant").get("id").asLong();
        tenantB = b.get("restaurant").get("id").asLong();
        String generated = a.get("generatedPassword").asText();
        assertEquals(12, generated.length());
        assertTrue(b.get("generatedPassword").isNull());
        owner = login("ada@gorilla.test", generated);
        ownerB = login("bo@other.test", "OtherPass123");

        JsonNode overview = GET("/api/admin/overview", admin, 200);
        assertEquals(2, overview.get("restaurants").asInt());
        // The platform console has no route to orders or stock.
        GET("/api/orders", admin, 403);
        GET("/api/stock", admin, 403);
        // Restaurant staff cannot reach the console.
        GET("/api/admin/overview", owner, 403);
        POST("/api/admin/restaurants", owner, Map.of(), 403);

        outlet1 = GET("/api/outlets", owner, 200).get(0).get("id").asLong();
        outletB = GET("/api/outlets", ownerB, 200).get(0).get("id").asLong();
    }

    @Test @Order(2)
    void ownerBuildsTeamAndMenu() throws Exception {
        outlet2 = POST("/api/outlets", owner, Map.of("name", "Gorilla Restaurant", "type", "RESTAURANT"), 201).get("id").asLong();
        JsonNode m = POST("/api/staff", owner, Map.of("firstName", "Mia", "lastName", "Manager", "email", "mia@gorilla.test",
                "role", "MANAGER", "outletIds", java.util.List.of(outlet1, outlet2), "password", "ManagerPass1"), 201);
        POST("/api/staff", owner, Map.of("firstName", "Max", "lastName", "Second", "email", "max@gorilla.test",
                "role", "MANAGER", "outletIds", java.util.List.of(outlet1), "password", "ManagerPass2"), 201);
        POST("/api/staff", owner, Map.of("firstName", "Wes", "lastName", "Waiter", "email", "wes@gorilla.test",
                "role", "WAITER", "outletIds", java.util.List.of(outlet1), "password", "WaiterPass12"), 201);
        // A waiter needs an outlet, and nobody can create an owner.
        POST("/api/staff", owner, Map.of("firstName", "No", "lastName", "Outlet", "email", "no@gorilla.test", "role", "WAITER"), 400);
        POST("/api/staff", owner, Map.of("firstName", "Eve", "lastName", "Boss", "email", "eve@gorilla.test", "role", "OWNER"), 403);
        manager = login("mia@gorilla.test", "ManagerPass1");
        manager2 = login("max@gorilla.test", "ManagerPass2");
        waiter = login("wes@gorilla.test", "WaiterPass12");

        // Fanta has two sizes, each with its own crate size; the dish is not counted in stock.
        JsonNode fanta = POST("/api/menu", owner, Map.of("name", "Fanta", "category", "Soft drinks", "hasSizes", true,
                "variants", java.util.List.of(variant("Small", 500, 24, true), variant("Large", 1000, 12, true))), 201);
        fantaSmall = fanta.get("variants").get(0).get("id").asLong();
        fantaLarge = fanta.get("variants").get(1).get("id").asLong();
        dish = POST("/api/menu", owner, Map.of("name", "Grilled fish", "hasSizes", false,
                "variants", java.util.List.of(variant("x", 6000, null, false))), 201).get("variants").get(0).get("id").asLong();
        // A product with sizes needs at least two.
        POST("/api/menu", owner, Map.of("name", "Water", "hasSizes", true, "variants", java.util.List.of(variant("Only", 300, null, true))), 400);
        // Managers create items and set their prices when new stock arrives; waiters cannot.
        GET("/api/menu", manager, 200);
        POST("/api/menu", waiter, Map.of("name", "X", "hasSizes", false, "variants", java.util.List.of(variant("x", 1, null, true))), 403);
        POST("/api/menu", manager, Map.of("name", "Manager Item", "hasSizes", false, "variants", java.util.List.of(variant("x", 700, 24, true))), 201);
        // Managers cannot add staff or outlets.
        GET("/api/staff", manager, 403);
        POST("/api/outlets", manager, Map.of("name", "Nope"), 403);

        customer = POST("/api/customers", owner, Map.of("name", "Chantal", "phone", "0788000000", "creditLimit", 100000, "creditDays", 14), 201)
                .get("id").asLong();
        // A manager may add a customer but credit terms are not theirs to set.
        tinyCustomer = POST("/api/customers", manager, Map.of("name", "Walk-in Joe", "creditLimit", 500000), 201).get("id").asLong();
        assertEquals(0, GET("/api/customers", manager, 200).get(1).get("creditLimit").asInt());
        assertNotNull(m.get("staff"));
    }

    @Test @Order(3)
    void receivingInCratesConvertsToBottlesAndShowsCratesPlusBottles() throws Exception {
        JsonNode r = POST("/api/stock/receive", manager, Map.of("outletId", outlet1, "variantId", fantaSmall, "quantity", 2,
                "unit", "PACK", "purchasePrice", 12000), 200);
        assertEquals(48, r.get("quantity").asInt());
        assertEquals("2 crates", r.get("display").asText());
        // A crate of 20 from a different supplier overrides the default of 24.
        JsonNode r2 = POST("/api/stock/receive", manager, Map.of("outletId", outlet1, "variantId", fantaSmall, "quantity", 1,
                "unit", "PACK", "unitsPerPackOverride", 20, "purchasePrice", 10000), 200);
        assertEquals(68, r2.get("quantity").asInt());
        assertEquals("2 crates + 20 bottles", r2.get("display").asText());
        JsonNode movements = GET("/api/stock/movements?outletId=" + outlet1, manager, 200);
        assertEquals(20, movements.get(0).get("unitsPerPackUsed").asInt());
        assertEquals(1, movements.get(0).get("enteredQuantity").asInt());
        assertEquals(20, movements.get(0).get("baseQuantity").asInt());
        // Waiters cannot receive stock; an untracked dish cannot be stocked.
        POST("/api/stock/receive", waiter, Map.of("outletId", outlet1, "variantId", fantaSmall, "quantity", 1, "unit", "BASE"), 403);
        POST("/api/stock/receive", manager, Map.of("outletId", outlet1, "variantId", dish, "quantity", 1, "unit", "BASE"), 400);
        // A pack size is needed to receive in packs.
        POST("/api/stock/receive", manager, Map.of("outletId", outlet1, "variantId", dish, "quantity", 1, "unit", "PACK"), 400);
        // Manager 2 is only assigned to outlet 1, so outlet 2 is out of bounds for them.
        POST("/api/stock/receive", manager2, Map.of("outletId", outlet2, "variantId", fantaSmall, "quantity", 1, "unit", "BASE"), 403);
    }

    @Test @Order(4)
    void approvalRulesAndSelfApprovalBlock() throws Exception {
        JsonNode order = POST("/api/orders", waiter, Map.of("outletId", outlet1, "customerId", customer, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", fantaSmall, "unit", "BASE", "quantity", 10),
                        Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 201);
        orderId = order.get("id").asLong();
        assertEquals("PENDING_APPROVAL", order.get("status").asText());
        assertEquals(11000, order.get("totalAmount").asInt()); // 10 x 500 + 6000, priced by the server
        // Delivering before approval is refused.
        POST("/api/orders/" + orderId + "/deliver", waiter, Map.of("amountPaid", 0), 409);
        // A waiter cannot approve; rejection needs a reason.
        POST("/api/orders/" + orderId + "/approve", waiter, null, 403);
        POST("/api/orders/" + orderId + "/reject", manager, Map.of(), 400);
        // Tenant B cannot see it at all.
        GET("/api/orders/" + orderId, ownerB, 404);
        assertEquals(0, GET("/api/orders", ownerB, 200).size());

        JsonNode approved = POST("/api/orders/" + orderId + "/approve", manager, null, 200);
        assertEquals("APPROVED", approved.get("status").asText());
        assertEquals(2, approved.get("history").size());
        // The order cannot be approved twice.
        POST("/api/orders/" + orderId + "/approve", manager2, null, 409);

        // An order a manager-turned-owner creates cannot be approved by the same person.
        JsonNode own = POST("/api/orders", owner, Map.of("outletId", outlet1, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 201);
        POST("/api/orders/" + own.get("id").asLong() + "/approve", owner, null, 403);
        POST("/api/orders/" + own.get("id").asLong() + "/reject", manager2, Map.of("reason", "Kitchen closed"), 200);
    }

    @Test @Order(5)
    void deliveryTakesStockRecordsPaymentAndOpensDebtForTheRest() throws Exception {
        // Not enough stock is refused and the order stays approved.
        JsonNode big = POST("/api/orders", waiter, Map.of("outletId", outlet1, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", fantaSmall, "unit", "BASE", "quantity", 500))), 201);
        long bigId = big.get("id").asLong();
        POST("/api/orders/" + bigId + "/approve", manager, null, 200);
        JsonNode refused = POST("/api/orders/" + bigId + "/deliver", waiter, Map.of("amountPaid", 250000), 409);
        assertTrue(refused.get("message").asText().contains("Not enough stock"));
        assertEquals("APPROVED", GET("/api/orders/" + bigId, manager, 200).get("status").asText());
        assertEquals(68, GET("/api/stock?outletId=" + outlet1, manager, 200).get(0).get("quantity").asInt());
        POST("/api/orders/" + bigId + "/cancel", waiter, Map.of("reason", "Customer left"), 200);

        // Partial payment without saying who owes the rest is refused... but the order has a customer, so it works.
        JsonNode delivered = POST("/api/orders/" + orderId + "/deliver", waiter, Map.of("amountPaid", 5000), 200);
        assertEquals("DELIVERED", delivered.get("status").asText());
        assertEquals("PARTIAL", delivered.get("paymentStatus").asText());
        assertEquals(5000, delivered.get("amountPaid").asInt());
        assertTrue(delivered.get("costOfGoods").asDouble() > 0);
        assertEquals(58, GET("/api/stock?outletId=" + outlet1, manager, 200).get(0).get("quantity").asInt());

        JsonNode debts = GET("/api/debts", manager, 200);
        assertEquals(1, debts.size());
        debtId = debts.get(0).get("id").asLong();
        assertEquals(6000, debts.get(0).get("outstanding").asInt()); // 11,000 bill - 5,000 paid
        assertEquals("Chantal", debts.get(0).get("customerName").asText());

        // A fully paid order gets a receipt number.
        JsonNode o2 = POST("/api/orders", waiter, Map.of("outletId", outlet1, "paymentMethod", "MOBILE_MONEY",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 2))), 201);
        long o2id = o2.get("id").asLong();
        POST("/api/orders/" + o2id + "/approve", manager, null, 200);
        JsonNode paid = POST("/api/orders/" + o2id + "/deliver", waiter, Map.of("amountPaid", 12000), 200);
        assertEquals("PAID", paid.get("paymentStatus").asText());
        assertTrue(paid.get("receiptNumber").asText().startsWith("RCP-"));
        // Paying nothing with nobody to owe it is refused.
        JsonNode o3 = POST("/api/orders", waiter, Map.of("outletId", outlet1, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 201);
        long o3id = o3.get("id").asLong();
        POST("/api/orders/" + o3id + "/approve", manager, null, 200);
        POST("/api/orders/" + o3id + "/deliver", waiter, Map.of("amountPaid", 0), 400);
        POST("/api/orders/" + o3id + "/deliver", waiter, Map.of("amountPaid", 99999), 400);
    }

    @Test @Order(6)
    void creditLimitIsEnforcedAndOnlyOwnerCanOverride() throws Exception {
        // Walk-in Joe has no credit at all (limit 0).
        JsonNode o = POST("/api/orders", waiter, Map.of("outletId", outlet1, "customerId", tinyCustomer, "paymentMethod", "CREDIT",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 201);
        long id = o.get("id").asLong();
        POST("/api/orders/" + id + "/approve", manager, null, 200);
        JsonNode refused = POST("/api/orders/" + id + "/deliver", manager, Map.of("amountPaid", 0, "overrideReason", "I know him"), 409);
        assertTrue(refused.get("message").asText().contains("credit limit"));
        assertEquals("APPROVED", GET("/api/orders/" + id, manager, 200).get("status").asText());
        // The owner needs a written reason.
        POST("/api/orders/" + id + "/deliver", owner, Map.of("amountPaid", 0), 409);
        JsonNode ok = POST("/api/orders/" + id + "/deliver", owner, Map.of("amountPaid", 0, "overrideReason", "Regular, pays on Friday"), 200);
        assertEquals("UNPAID", ok.get("paymentStatus").asText());
    }

    @Test @Order(7)
    void debtPaymentsAreAppendOnlyAndReversible() throws Exception {
        JsonNode d = POST("/api/debts/" + debtId + "/payments", waiter, Map.of("amount", 2000, "method", "CASH"), 200);
        assertEquals(4000, d.get("debt").get("outstanding").asInt());
        assertEquals("PARTIAL", d.get("debt").get("status").asText());
        paymentId = d.get("payments").get(0).get("id").asLong();
        POST("/api/debts/" + debtId + "/payments", waiter, Map.of("amount", 99999, "method", "CASH"), 400);

        // Managers cannot reverse, discount or write off.
        POST("/api/payments/" + paymentId + "/reverse", manager, Map.of("reason", "oops"), 403);
        POST("/api/debts/" + debtId + "/adjustments", manager, Map.of("type", "DISCOUNT", "amount", 100, "reason", "x"), 403);
        // The owner must give a reason.
        POST("/api/debts/" + debtId + "/adjustments", owner, Map.of("type", "DISCOUNT", "amount", 100), 400);

        JsonNode rev = POST("/api/payments/" + paymentId + "/reverse", owner, Map.of("reason", "Entered on wrong debt"), 200);
        assertEquals(6000, rev.get("debt").get("outstanding").asInt());
        assertEquals(2, rev.get("payments").size()); // the original stays; a reversal entry was added
        POST("/api/payments/" + paymentId + "/reverse", owner, Map.of("reason", "again"), 409);
        // No update or delete routes exist for payments.
        mvc.perform(delete("/api/payments/" + paymentId).header("Authorization", "Bearer " + owner)).andExpect(
                result -> assertTrue(result.getResponse().getStatus() >= 400));

        POST("/api/debts/" + debtId + "/payments", manager, Map.of("amount", 1000, "method", "MOBILE_MONEY"), 200);
        JsonNode disc = POST("/api/debts/" + debtId + "/adjustments", owner, Map.of("type", "DISCOUNT", "amount", 1000, "reason", "Loyal customer"), 200);
        assertEquals(4000, disc.get("debt").get("outstanding").asInt());
        JsonNode off = POST("/api/debts/" + debtId + "/adjustments", owner, Map.of("type", "WRITE_OFF", "amount", 4000, "reason", "Customer moved away"), 200);
        assertEquals("WRITTEN_OFF", off.get("debt").get("status").asText());
        assertEquals(0, off.get("debt").get("outstanding").asInt());

        JsonNode statement = GET("/api/customers/" + customer + "/statement", owner, 200);
        assertEquals(6000, statement.get("totalBilled").asInt());
    }

    @Test @Order(8)
    void stockTransfersAdjustmentsNeedReasonsAndNeverGoNegative() throws Exception {
        POST("/api/stock/transfer", manager, Map.of("fromOutletId", outlet1, "toOutletId", outlet2, "variantId", fantaSmall,
                "quantity", 1, "unit", "PACK"), 200);
        assertEquals(34, stockOf(outlet1, fantaSmall));
        assertEquals(24, stockOf(outlet2, fantaSmall));
        POST("/api/stock/transfer", manager, Map.of("fromOutletId", outlet1, "toOutletId", outlet2, "variantId", fantaSmall,
                "quantity", 100, "unit", "PACK"), 409);
        POST("/api/stock/adjust", manager, Map.of("outletId", outlet1, "variantId", fantaSmall, "type", "BREAKAGE",
                "quantity", 3, "unit", "BASE", "reason", ""), 400);
        POST("/api/stock/adjust", manager, Map.of("outletId", outlet1, "variantId", fantaSmall, "type", "BREAKAGE",
                "quantity", 3, "unit", "BASE", "reason", "Dropped a crate"), 200);
        assertEquals(31, stockOf(outlet1, fantaSmall));
        POST("/api/stock/adjust", manager, Map.of("outletId", outlet1, "variantId", fantaSmall, "type", "CORRECTION",
                "quantity", 30, "unit", "BASE", "reason", "Monthly count"), 200);
        assertEquals(30, stockOf(outlet1, fantaSmall));
        POST("/api/stock/adjust", manager, Map.of("outletId", outlet1, "variantId", fantaSmall, "type", "WASTE",
                "quantity", 31, "unit", "BASE", "reason", "Expired"), 409);
        // Low stock alert: threshold is 5 and outlet 1 holds 30, so nothing is low yet.
        assertEquals(0, GET("/api/stock?lowOnly=true&outletId=" + outlet1, manager, 200).size());
    }

    long stockOf(long outletId, long variantId) throws Exception {
        for (JsonNode row : GET("/api/stock?outletId=" + outletId, owner, 200)) {
            if (row.get("variantId").asLong() == variantId) return row.get("quantity").asLong();
        }
        return 0;
    }

    @Test @Order(9)
    void waiterSeesOnlyOwnOrdersAndOutlet() throws Exception {
        for (JsonNode o : GET("/api/orders", waiter, 200)) assertEquals("Wes Waiter", o.get("createdByName").asText());
        // A manager (who does not take orders) sees every order of their outlets.
        assertTrue(GET("/api/orders", manager, 200).size() > 1);
        GET("/api/orders?outletId=" + outlet2, waiter, 403);
        POST("/api/orders", waiter, Map.of("outletId", outlet2, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 403);
        GET("/api/dashboard", waiter, 403);
    }

    @Test @Order(10)
    void pausedOutletTakesNoOrdersAndApprovalCanBeSwitchedOff() throws Exception {
        PUT("/api/outlets/" + outlet2 + "/status", owner, Map.of("status", "PAUSED"), 200);
        POST("/api/orders", owner, Map.of("outletId", outlet2, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 409);
        PUT("/api/outlets/" + outlet2 + "/status", owner, Map.of("status", "OPEN"), 200);
        // Only the owner may change business settings.
        PUT("/api/settings", manager, Map.of("approvalRequired", false), 403);
        PUT("/api/settings", owner, Map.of("approvalRequired", false), 200);
        JsonNode auto = POST("/api/orders", waiter, Map.of("outletId", outlet1, "paymentMethod", "CASH",
                "lines", java.util.List.of(Map.of("variantId", dish, "unit", "BASE", "quantity", 1))), 201);
        assertEquals("APPROVED", auto.get("status").asText());
        PUT("/api/settings", owner, Map.of("approvalRequired", true), 200);
    }

    @Test @Order(10)
    void expensesAreRecordedReportedAndOnlyVoidedByOwner() throws Exception {
        JsonNode e = POST("/api/expenses", manager, Map.of("outletId", outlet1, "category", "Electricity", "amount", 15000,
                "description", "October bill"), 201);
        long id = e.get("id").asLong();
        POST("/api/expenses", waiter, Map.of("outletId", outlet1, "category", "Gas", "amount", 100), 403);
        POST("/api/expenses", manager, Map.of("outletId", outlet2, "category", "Gas", "amount", 100), 201);
        POST("/api/expenses", manager, Map.of("outletId", outlet1, "category", "Gas", "amount", 0), 400);
        POST("/api/expenses/" + id + "/void", manager, Map.of("reason", "x"), 403);
        POST("/api/expenses/" + id + "/void", owner, Map.of(), 400);
        assertEquals(15100, GET("/api/dashboard", owner, 200).get("kpis").get("expenses").asInt());
        POST("/api/expenses/" + id + "/void", owner, Map.of("reason", "Entered twice"), 200);
        assertEquals(100, GET("/api/dashboard", owner, 200).get("kpis").get("expenses").asInt());
        assertEquals(0, GET("/api/dashboard", ownerB, 200).get("kpis").get("expenses").asInt());
        assertTrue(GET("/api/reports/expenses", owner, 200).get("headers").size() > 0);
        assertEquals(0, GET("/api/expenses", ownerB, 200).size());
    }

    @Test @Order(10)
    void everyPageInTheMenuLoadsForThoseWhoSeeIt() throws Exception {
        // The app shows a page only when the person has its permission; each such page must then load.
        String[][] pages = {{"dashboard:view", "/api/dashboard"}, {"stock:view", "/api/stock"}, {"menu:view", "/api/menu"},
                {"customer:view", "/api/customers"}, {"debt:view", "/api/debts"}, {"expense:manage", "/api/expenses"},
                {"report:view", "/api/reports/sales"}, {"outlet:manage", "/api/outlets"}, {"staff:manage", "/api/staff"},
                {"audit:view", "/api/audit"}};
        for (String token : new String[]{owner, manager, waiter}) {
            JsonNode perms = GET("/api/me", token, 200).get("permissions");
            java.util.Set<String> has = new java.util.HashSet<>();
            perms.forEach(n -> has.add(n.asText()));
            for (String[] page : pages) if (has.contains(page[0])) GET(page[1], token, 200);
            if (has.contains("order:create") || has.contains("order:view-all")) GET("/api/orders", token, 200);
        }
    }

    @Test @Order(10)
    void waiterCanAddOrFindACustomerWhileTakingAnOrder() throws Exception {
        POST("/api/customers/quick", waiter, Map.of(), 400);
        JsonNode a = POST("/api/customers/quick", waiter, Map.of("name", "Jean", "phone", "0788111222"), 200);
        JsonNode again = POST("/api/customers/quick", waiter, Map.of("phone", "0788111222"), 200);
        assertEquals(a.get("id").asLong(), again.get("id").asLong());
        assertEquals(0, a.get("creditLimit").asInt());
        JsonNode phoneOnly = POST("/api/customers/quick", waiter, Map.of("phone", "0788333444"), 200);
        assertEquals("0788333444", phoneOnly.get("name").asText());
        assertEquals(a.get("id").asLong(), POST("/api/customers/quick", waiter, Map.of("name", "jean"), 200).get("id").asLong());
        assertEquals(0, GET("/api/customers", ownerB, 200).size());
    }

    @Test @Order(11)
    void repeatedWrongPasswordsAreBlockedTemporarily() throws Exception {
        for (int i = 0; i < 5; i++) POST("/api/auth/login", null, Map.of("email", "max@gorilla.test", "password", "wrong-" + i), 401);
        POST("/api/auth/login", null, Map.of("email", "max@gorilla.test", "password", "ManagerPass2"), 429);
        POST("/api/auth/login", null, Map.of("email", "nobody@gorilla.test", "password", "x"), 401);
    }

    @Test @Order(11)
    void dashboardAndReportsAndExport() throws Exception {
        JsonNode d = GET("/api/dashboard", owner, 200);
        assertTrue(d.get("kpis").get("revenue").asDouble() > 0);
        assertTrue(d.get("kpis").has("stockValue"));
        assertFalse(d.get("kpis").has("estimatedProfit"));
        assertFalse(d.toString().contains("supplier"));
        assertTrue(d.get("outlets").size() >= 1);
        assertTrue(d.get("bestSellers").size() >= 1);
        // A manager sees their outlets only and gets reports; business B sees none of A's numbers.
        GET("/api/dashboard", manager, 200);
        assertEquals(0, GET("/api/dashboard", ownerB, 200).get("kpis").get("orders").asInt());
        for (String type : new String[]{"sales", "products", "stock", "movements", "reconciliation", "debts", "aging", "payments"}) {
            JsonNode t = GET("/api/reports/" + type, owner, 200);
            assertTrue(t.get("headers").size() > 0, type);
        }
        GET("/api/reports/audit", manager, 403);
        GET("/api/reports/audit", owner, 200);
        byte[] bytes = mvc.perform(get("/api/reports/sales/export").header("Authorization", "Bearer " + owner))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 1000);
        assertEquals('P', (char) bytes[0]); // an xlsx file is a zip archive
    }

    @Test @Order(12)
    void auditTrailIsChainedAndTamperingIsDetected() throws Exception {
        JsonNode ok = GET("/api/audit/verify", owner, 200);
        assertTrue(ok.get("valid").asBoolean(), ok.toString());
        assertTrue(ok.get("entries").asInt() > 20);
        GET("/api/audit/verify", manager, 403);
        JsonNode trail = GET("/api/audit", owner, 200);
        assertTrue(trail.toString().contains("order.delivered"));
        assertTrue(trail.toString().contains("debt.payment_reversed"));
        // Business B's trail holds none of A's records.
        assertFalse(GET("/api/audit", ownerB, 200).toString().contains("order.delivered"));

        // Someone with database access edits a record: the chain no longer verifies.
        jdbc.update("update audit_log set reason = 'tampered' where chain_key = ? and action = 'order.delivered'", tenantA);
        JsonNode broken = GET("/api/audit/verify", owner, 200);
        assertFalse(broken.get("valid").asBoolean());
        assertNotNull(broken.get("brokenAtSeq"));
        assertTrue(GET("/api/audit/verify", ownerB, 200).get("valid").asBoolean());
    }

    @Test @Order(13)
    void suspendingABusinessBlocksEveryoneThereImmediately() throws Exception {
        POST("/api/admin/restaurants/" + tenantA + "/suspend", admin, Map.of("reason", "Unpaid"), 200);
        GET("/api/orders", waiter, 401);
        GET("/api/me", owner, 401);
        POST("/api/auth/login", null, Map.of("email", "wes@gorilla.test", "password", "WaiterPass12"), 403);
        // Business B is unaffected.
        GET("/api/me", ownerB, 200);
        POST("/api/admin/restaurants/" + tenantA + "/reactivate", admin, null, 200);
        String again = login("wes@gorilla.test", "WaiterPass12");
        GET("/api/orders", again, 200);

        JsonNode reset = POST("/api/admin/restaurants/" + tenantA + "/reset-owner-password", admin, null, 200);
        String fresh = reset.get("generatedPassword").asText();
        assertEquals(12, fresh.length());
        POST("/api/auth/login", null, Map.of("email", "ada@gorilla.test", "password", fresh), 200);
        PUT("/api/admin/restaurants/" + tenantA, ownerB, Map.of("name", "X"), 403);
        PUT("/api/admin/restaurants/" + tenantA, admin, Map.of("name", "Other Place"), 409);
        JsonNode edited = PUT("/api/admin/restaurants/" + tenantA, admin, Map.of("name", "Gorilla Bar Renamed", "phone", "0788000111",
                "address", "Kigali", "ownerFirstName", "Adaline", "ownerLastName", "Owner", "ownerEmail", "ada@gorilla.test"), 200);
        assertEquals("Gorilla Bar Renamed", edited.get("name").asText());
        assertEquals("Adaline Owner", edited.get("ownerName").asText());
        JsonNode detail = GET("/api/admin/restaurants/" + tenantA, admin, 200);
        assertEquals(2, detail.get("outlets").size());
        assertTrue(detail.get("staff").size() >= 4);
        assertTrue(detail.get("roles").size() >= 4);
    }
}
