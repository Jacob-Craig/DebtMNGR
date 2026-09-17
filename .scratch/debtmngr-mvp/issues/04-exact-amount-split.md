# 04: Exact Amount Split Mode

**What to build:** Adds the "Exact Amounts" mode to the expense form, allowing users to specify uneven splits with strict sum validation.

**Blocked by:** 03: Equal-Split Expense Recording & Balances

**Status:** resolved

- [x] The Expense form allows toggling between "Equal" and "Exact" split modes.
- [x] In "Exact" mode, the user inputs precise currency amounts for each selected consumer.
- [x] The service layer strictly validates that the sum of the exact amounts equals the total Transaction amount before saving.
- [x] If invalid, the form displays a validation error preventing submission.
- [x] If valid, balanced Entries are saved according to the custom amounts.

## Comments
- Implemented pure domain enum `SplitMode` (`EQUAL`, `EXACT`) and `ExactSplitCalculator` enforcing strict sum conservation ($\sum \text{exact amounts} == \text{total amount}$), non-negative splits, and boundary edge cases.
- Extended `TransactionService.createExpense` to support exact amount splits, loading group participants, validating group membership, and enforcing double-entry ledger balance ($\sum \text{debits} == \sum \text{credits} == \text{amount}$).
- Updated `CreateExpenseForm` and `TransactionController` with split mode toggling, server-side validation against sum mismatches and negative values, and Thymeleaf error binding.
- Updated `groups/transactions/new.html` with tabbed split mode toggle, dynamic client-side live sum calculation, and client-side submit prevention when split amounts do not balance.
- Built comprehensive test suite: domain unit tests (`ExactSplitCalculatorTest`), service unit tests (`TransactionServiceTest`), web controller unit tests (`TransactionControllerTest`), and full Spring Boot integration test (`ExactSplitExpenseIntegrationTest`).
- Ran two-axis code review (Standards and Spec) and resolved all findings (eliminated `!!` force-unwrapping, deduplicated participant loading in service layer, and ensured client-side validation prevents invalid form submission).

