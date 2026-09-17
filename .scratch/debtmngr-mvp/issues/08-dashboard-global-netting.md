# 08: Dashboard Global Netting & Cross-Group Settlement

**What to build:** The main Dashboard shows a consolidated global net balance across all shared groups for the operator. Users can trigger a global "Settle Up" which allocates proportional settlement transactions into each group's ledger.

**Blocked by:** 06: Intra-Group Settlement & Locking

**Status:** resolved

- [x] The Dashboard aggregates the operator's (`isSelf = true`) net Balance across all Groups.
- [x] The Dashboard lists a cross-group breakdown (e.g. "Bob owes you £15 total").
- [x] A global "Settle Up" button allows the operator to record a lump-sum payment with a specific contact (e.g. Bob).
- [x] The service layer automatically allocates the global settlement amount into proportional `SETTLEMENT` transactions within each Group where a debt exists between the two participants.
- [x] The cross-group breakdown and individual Group ledgers correctly reflect the settled amounts.

## Comments
- Implemented `ProportionalSettlementCalculator` in `domain` using the largest-remainder method (Hamilton-Hare) with overflow-safe `BigInteger` math to proportionally allocate lump-sum settlements across multiple groups while strictly conserving sums down to the exact penny.
- Implemented `TransactionService.getPairwiseDebtsWithOperator`, `getGlobalNetSummary`, and `createGlobalSettlement` to calculate pairwise contact debts across all shared groups and create proportional `SETTLEMENT` transactions that lock unsettled prior transactions in each group ledger.
- Implemented `GlobalSettlementController`, `GlobalSettleUpForm`, and Thymeleaf templates `settle.html` and `dashboard.html` with consolidated net balance hero banner, per-contact cross-group debt cards with group breakdown badges, and global settle-up flow.
- Followed bottom-up TDD with unit tests (`ProportionalSettlementCalculatorTest`, `TransactionServiceGlobalNettingTest`), Web MVC tests (`DashboardControllerTest`, `GlobalSettlementControllerTest`), and full end-to-end integration test (`GlobalNettingIntegrationTest`).
