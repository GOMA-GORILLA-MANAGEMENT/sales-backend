# SALES: Bar & Restaurant Management (backend)

Spring Boot 3 · Java 21 · PostgreSQL · JWT. One platform, many separate restaurant/bar businesses.

## Run it locally

```bash
# 1. Wipe the old local database (local only, no production data): see scripts/reset-local-db.sql
psql -h localhost -U eshopuser -d eshop -f scripts/reset-local-db.sql

# 2. Create .env from .env.example (it is git-ignored) with your database, JWT key, mail password and
#    SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD, then start the API. Works on Java 21 or newer.
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

No secret is stored in the code: database, JWT and mail settings come from environment variables or the git-ignored `.env`
(`SPRING_DATASOURCE_URL`, `APPLICATION_SECURITY_JWT_SECRET_KEY`, ...). The `dev` profile also allows the Vite dev server
(`http://localhost:5173`) and turns Swagger on.

Tests (`mvn test`) run the whole flow on an in-memory H2 database.

## Who can do what

| Role | Scope |
|---|---|
| `SUPER_ADMIN` | Platform console only: creates restaurant accounts, suspends/reactivates them, resets an owner's password. Cannot see orders or stock. |
| `OWNER` | Everything inside their business, including business settings and credit-limit overrides. |
| `OVERALL_MANAGER` | Everything the owner can do except business settings; all outlets or the ones assigned. |
| `MANAGER` | One or a few assigned outlets: approve/reject/cancel orders, receive stock (creating new items and setting their prices), move stock, breakage and waste, customers, debt payments, outlet dashboard. |
| `WAITER` | Their outlet: take orders, cancel their own, record delivery, take payments. |

Permissions live in `model/enums/Role.java` and `Permission.java`. Services check them with `Access.require(...)`.

## The order flow

1. Waiter sends an order → `PENDING_APPROVAL` (or `APPROVED` straight away if the owner switched approval off).
2. A different manager approves (never the creator) or rejects (reason required) → `APPROVED` = "to serve".
3. `POST /api/orders/{id}/deliver` in one transaction: checks the credit limit, takes stock off the shelf (refused if not enough),
   works out cost of goods, records the payment (receipt number) and opens a debt for any unpaid amount.
4. Every status change is kept with who, when and why.

## Stock

Stock is counted in the smallest unit (bottles). `StockService` converts packs (crates) on the way in and writes both the
entered and the converted quantity to `stock_movements`. A receipt may override the pack size (a crate of 20 instead of 24).
Negative stock is refused unless the business allows it.

## Debts

Partial payments, receipts, reversals instead of edits, discounts / write-offs / corrections with a reason.
`customers.credit_limit = 0` means no credit.

## Audit trail

`audit_log` is append-only and hash-chained per business. `GET /api/audit/verify` recomputes the chain and reports the first
altered or missing record.

## Business isolation

`TenantContext` is set from the signed-in user on every request. Records carry `tenant_id`, repositories always filter by it,
and a Hibernate filter adds it as a second guard. A suspended business is refused at login and on every request.

## Reports

`GET /api/reports/{sales|products|stock|movements|reconciliation|debts|aging|payments|audit}` returns a table;
`.../export` downloads it as Excel. The web app builds the PDF from the same table.
