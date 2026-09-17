# 05: System & Custom Categories

**What to build:** Seeds the database with translated System Categories. Users can select a Category when recording an expense, create group-scoped custom categories, and filter the Group activity log.

**Blocked by:** 03: Equal-Split Expense Recording & Balances

**Status:** resolved

- [x] A `Category` entity exists with system seed data (Groceries, Utilities, Transport, etc.).
- [x] The Expense form includes a dropdown to select an existing System Category or create a custom one scoped to the Group.
- [x] The Group activity log displays the category for each Transaction.
- [x] The Group activity log can be filtered by Category.

## Comments
- Implemented pure domain entity `Category` with system category flag, unique system key, and group scoping invariants (`isAvailableIn(group)`).
- Implemented `CategoryRepository` with optimized available-for-group queries (ordering system categories first, followed by group custom categories) and entity graphs on `TransactionRepository`.
- Implemented `CategoryService` with startup seeding via `CategoryDataInitializer` for default system categories (Groceries, Dining Out, Utilities, Transport, Entertainment, General) and group-scoped custom category creation.
- Extended `TransactionService.createExpense` to associate system and group custom categories, verifying group boundaries, and added category filtering in `TransactionService.getTransactionsForGroup`.
- Updated web presentation: `CreateExpenseForm` and `TransactionController` supporting category selection and dynamic custom category creation; updated `GroupController` and `groups/show.html` to display category badges in the activity log and provide interactive category filtering with active state indicators.
- Built bottom-up TDD suite: domain unit tests (`CategoryTest`, `TransactionTest`, `GroupTest`), repository tests (`CategoryRepositoryTest`, `TransactionRepositoryTest`), service unit tests (`CategoryServiceTest`, `TransactionServiceTest`), web controller unit tests (`TransactionControllerTest`, `GroupControllerTest`), and full end-to-end integration test (`CategoryExpenseIntegrationTest`).
- Conducted two-axis code review (Standards and Spec) and resolved all feedback: eliminated all `!!` force unwraps, added validation for blank custom category names when creating custom categories, encapsulated category boundary checks in `isAvailableIn`, and displayed "Uncategorized" for expenses without a category.
