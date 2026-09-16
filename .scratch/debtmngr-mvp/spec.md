# Spec: DebtMNGR MVP - Shared Expense & Debt Management

Status: ready-for-agent

## Problem Statement

When friends, roommates, and travel companions share expenses, tracking who paid for what and who owes whom quickly becomes confusing and prone to disputes. Simple expense trackers either lack mathematical rigor (leading to balance discrepancies) or introduce overwhelming accounting complexity (charts of accounts, double-sided invoice matching). Users need a clean, minimalist way to record shared expenses, distribute costs among participants, see a consolidated view of net balances across groups, and settle debts with complete historical auditability.

## Solution

DebtMNGR provides a minimalist web application backed by a double-entry mutual debt clearing engine. Users organize shared financial activities into Groups and record Transactions with balanced debits and credits. Costs can be split equally (with automatic penny remainder assignment to the payer) or by exact amounts. Settlements clear debts directly, and the dashboard provides smart cross-group settlement allocation. To ensure historical integrity, settled transactions are locked, and any subsequent corrections are made via linked Adjustment transactions with complete audit logging.

## User Stories

1. As a participant, I want to create a new Group with a name and description, so that I can isolate shared expenses for a specific occasion or living arrangement (e.g. "Apartment", "Trip to Spain").
2. As a participant, I want to view a list of all my active Groups along with my current net Balance in each, so that I can quickly see where I stand financially.
3. As a participant, I want to add new Participants to a Group, so that everyone sharing costs is represented in the ledger.
4. As the operator, I want my own identity to be represented as a Participant within each Group with an `isSelf` indicator, so that my personal balance is clearly distinguished from other participants.
5. As a participant, I want to record an expense Transaction with a description, total amount, date, payer, and category, so that shared costs are captured in the ledger.
6. As a participant, I want to assign a Category from a list of standard system defaults (e.g. Groceries, Dining Out, Utilities, Transport, Entertainment, General) to an expense, so that expenses are consistently classified.
7. As a participant, I want to create custom Categories scoped to a Group, so that group-specific costs can be classified without polluting other groups.
8. As a participant, I want to split an expense Transaction equally among selected participants, so that common group bills can be divided quickly with minimal input.
9. As a participant, I want the system to allocate any odd penny rounding remainders to the Payer on equal splits, so that total debits and credits always balance exactly to the penny.
10. As a participant, I want to split an expense Transaction by exact currency amounts, so that unevenly distributed costs can be precisely recorded.
11. As a participant, I want the system to validate that the sum of exact split shares equals the total Transaction amount before saving, so that invalid, unbalanced entries cannot enter the ledger.
12. As a participant, I want each recorded Transaction to generate balanced ledger Entries where total debits equal total credits, so that the double-entry invariant is preserved at all times.
13. As a participant, I want to view the Group page showing each participant's current Balance (positive for owed money, negative for owing money), so that debt status is immediately transparent.
14. As a participant, I want to view an activity log of all historical Transactions within a Group, so that everyone can audit past expenses.
15. As a participant, I want to filter the Group activity log by Category and Participant, so that I can quickly inspect specific types of spending.
16. As a participant, I want to view the detailed breakdown of a Transaction showing the exact debit and credit Entries, so that the underlying accounting math is fully inspectable.
17. As a participant, I want to edit an unsettled Transaction, so that accidental typos in descriptions, amounts, or splits can be corrected.
18. As a participant, I want the system to record an AuditLog entry whenever a Transaction is edited, preserving the previous state and timestamp, so that an audit trail of modifications is maintained.
19. As a participant, I want to soft-delete an unsettled Transaction, so that erroneous records are removed from active Balance calculations without permanently destroying history.
20. As a participant, I want to record a Settlement between two participants within a Group, so that a payment can be formally recognized and reduce outstanding debt.
21. As a participant, I want the system to automatically lock all prior Transactions between the settling participants when a Settlement is recorded, so that settled debts cannot be retroactively altered or corrupted.
22. As a participant, I want to create an Adjustment Transaction linked to a locked, settled Transaction when an expense needs correction, so that additional owed amounts are reflected in the current Balance without rewriting historical ledger records.
23. As a participant, I want to view my consolidated net Balance across all shared Groups on the main Dashboard, so that I know my overall financial position at a glance.
24. As a participant, I want to see a consolidated list on the Dashboard of who owes me what and whom I owe across all groups, so that I do not have to check groups individually.
25. As a participant, I want to record a consolidated lump-sum Settlement to another participant directly from the Dashboard, so that I can settle all debts with that person in a single payment.
26. As a participant, I want the system to automatically allocate a consolidated Dashboard Settlement into proportional Settlement transactions inside each underlying Group, so that each group ledger is independently brought to balance.

## Implementation Decisions

### Domain Architecture & Ledger Model
- The application implements a pure Kotlin domain layer decoupled from framework and web concerns.
- A mutual debt clearing ledger model is used: Accounts exist solely for Participants within Groups. No nominal expense accounts or chart-of-accounts hierarchies exist.
- Invariant: Every Transaction consists of balanced Entries where $\sum \text{debits} = \sum \text{credits}$.
- Balance calculation: An Account's Balance is derived directly from its Entries as $\sum \text{credits} - \sum \text{debits}$. A positive balance indicates the Participant is owed money; a negative balance indicates the Participant owes money.
- Currencies are handled strictly as integer minor units (pence/cents stored as 64-bit `Long`) to eliminate IEEE 754 floating-point rounding errors.

### Entities and Relationships
- **Group**: Contains a name, description, creation timestamp, a collection of Participants, a collection of Transactions, and group-scoped Categories.
- **Participant**: Belongs to a Group, contains a display name, an `isSelf` boolean flag indicating the current operator, and has an associated Account.
- **Account**: Scoped to a Participant within a Group, holds the reference to all debit/credit Entries that determine the Participant's balance.
- **Category**: First-class entity containing an optional system key (e.g. `GROCERIES`, `UTILITIES`) or group reference for custom categories, a default name, and icon/color styling.
- **Transaction**: Belongs to a Group, has a description, total amount, timestamp, Payer reference, optional Category reference, TransactionType (`EXPENSE`, `SETTLEMENT`, `ADJUSTMENT`), `isLocked` flag, `isDeleted` flag, a collection of balanced Entries, and an optional reference to an original Transaction if it is an Adjustment.
- **Entry**: Belongs to a Transaction and references an Account, containing an EntryType (`DEBIT`, `CREDIT`) and an amount in minor units.
- **AuditLog**: Immutable entity recording the transaction reference, action type (`EDIT`, `DELETE`), serialized prior state, timestamp, and optional reason.

### Splitting & Remainder Allocation
- Equal Split: Total amount is divided by the number of participants. Remainder pennies ($\text{total} \pmod N$) are added to the Payer's share if the Payer is in the split, or to the first participant in the list if the Payer is excluded.
- Exact Split: Each participant is assigned an explicit integer amount. The service layer validates that the sum of all individual shares equals the total amount before persisting.

### Settlements & Reconciliation Locking
- Intra-Group Settlement: A Transaction with type `SETTLEMENT` recording a credit to the paying participant and a debit to the receiving participant for the settled amount. Upon persistence, all preceding unsettled transactions involving both participants within that group are marked `isLocked = true`.
- Cross-Group Dashboard Settlement: When settling a consolidated amount between two participants across multiple groups, the service queries all groups where an outstanding bilateral debt exists and disaggregates the payment into proportional Settlement transactions in each group.
- Adjustment Transactions: When an expense modification is requested on a locked Transaction, the system prevents direct mutation and creates a new Transaction of type `ADJUSTMENT` containing only the delta amounts, linked via foreign key to the original Transaction.

### Presentation & User Interface
- Built with Spring Boot WebMVC and server-rendered Thymeleaf templates.
- Views include:
  1. `/` - Dashboard with consolidated balances, cross-group debtor/creditor breakdown, multi-group settle modal, and group cards.
  2. `/groups/{id}` - Group detail view with participant balances, filterable transaction ledger (by category/participant), and actions to add expenses or settle.
  3. `/groups/{id}/transactions/new` - Expense creation form supporting equal and exact split modes.
  4. `/transactions/{id}` - Transaction detail view displaying balanced entries, audit history, and adjustment creation for locked transactions.

## Testing Decisions

### What Makes a Good Test
- Tests must verify observable behavior through public interfaces rather than asserting private implementation details.
- Financial calculations (double-entry equality, remainder allocation, balance derivation, multi-group allocation) must be validated with comprehensive boundary condition scenarios (e.g. 1 penny splits, 3-way remainder distributions, multi-group partial settlements).

### Testing Seams
1. **Primary Domain Seam (Unit Tests)**: Pure Kotlin tests verifying the domain invariants:
   - Double-entry balance validation ($\sum \text{debits} == \sum \text{credits}$).
   - Equal split rounding algorithms and remainder allocation.
   - Balance derivation from ledger entries.
   - Immutable audit logging and state snapshotting.
2. **Business Coordination Seam (Service Layer Tests)**: Service tests isolated with mock repositories or transactional tests:
   - Transaction creation, validation, and entry generation.
   - Settlement execution and automatic reconciliation locking.
   - Cross-group settlement distribution logic.
   - Adjustment creation for locked transactions.
3. **Web / Controller Seam (`@WebMvcTest`)**:
   - Verification of HTTP endpoints, form parameter binding, input validation error handling, and Thymeleaf model attribute provisioning.

## Out of Scope

- Multi-currency conversions and foreign exchange rates (all amounts are in the configured default currency).
- Multi-user authentication, user registration, JWT/session logins, and password management (deferred to subsequent iteration; domain uses `isSelf` flag on Participant).
- Automatic transitive debt simplification graphs (debts remain strictly pairwise).
- External payment gateway integrations (e.g. Stripe, PayPal, bank transfers; settlements record payment recognition only).
- Recurring automated transactions or subscription scheduling.
- File attachments (receipt images, PDFs).

## Further Notes

- Schema migration: Initial development utilizes Spring Data JPA with `spring.jpa.hibernate.ddl-auto=update` as configured in `application.properties`. Flyway migrations will be established prior to production packaging.
- Future multi-user upgrade path: An authenticated `User` entity will simply reference `Participant` records across groups via a mapping table without altering the double-entry accounting engine.
