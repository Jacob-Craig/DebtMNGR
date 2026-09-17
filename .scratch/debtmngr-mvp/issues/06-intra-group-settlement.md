# 06: Intra-Group Settlement & Locking

**What to build:** Users can click "Settle Up" inside a Group. This creates a SETTLEMENT transaction, clears the debt between two participants, and marks their prior unsettled transactions in that group as locked.

**Blocked by:** 03: Equal-Split Expense Recording & Balances

**Status:** resolved

- [x] A "Settle Up" modal/page in the Group allows selecting a Payer, a Receiver, and an amount.
- [x] Submitting the form saves a `Transaction` of type `SETTLEMENT` (crediting the Payer, debiting the Receiver).
- [x] Upon saving, the service identifies all prior unlocked transactions involving both participants in that Group and marks them `isLocked = true`.
- [x] The dashboard and group page accurately reflect the new zeroed or reduced balances.

## Comments
- Implemented intra-group settlement flow with domain validation in `TransactionService.createSettlement` ensuring double-entry balance ($\sum \text{debits} == \sum \text{credits} == \text{amount}$).
- Created `TransactionRepository.findUnlockedTransactionsBetweenAccounts` to identify unlocked, non-deleted prior transactions strictly between the two participants (paired CREDIT/DEBIT entries) prior to the settlement timestamp, and encapsulated locking via `Transaction.lock()`.
- Implemented `SettlementController` and `SettleUpForm` with validation (positive amount, non-null payer/receiver, different participants, membership in group) and template `groups/settle.html`.
- Updated `groups/show.html` with "Settle Up" header action, per-participant "Settle" shortcuts in the roster table, "Settlement" badge indicator, and "Locked" transaction status badges.
- Updated `DashboardController` and `dashboard.html` to compute and display the operator's current net balance for each group on the group cards.
- Followed bottom-up TDD with unit tests (`TransactionTest`, `TransactionServiceTest`), JPA repository test (`TransactionRepositoryTest`), Web MVC unit tests (`SettlementControllerTest`, `DashboardControllerTest`), and full end-to-end integration test (`IntraGroupSettlementIntegrationTest`).
- Completed parallel two-axis code review (Standards & Spec) and addressed all findings: refined locking query precision to exclude 3rd-party expenses, enforced settlement precedence filter, added `Transaction.lock()` domain method, and extracted `Long.toFormattedBalanceModel()` in `Formatters.kt`.

