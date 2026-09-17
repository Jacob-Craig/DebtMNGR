# 03: Equal-Split Expense Recording & Balances

**What to build:** The core double-entry accounting engine. Users can record a new Expense (Equal Split only). The system creates a Transaction with balanced debit/credit Entries, handles odd-penny remainders, and displays each Participant's net Balance on the Group page.

**Blocked by:** 02: Group Roster & Participants

**Status:** resolved

- [x] A user can open an Expense form in the Group, specifying a description, amount, Payer, and selected consumers.
- [x] The service layer divides the amount equally among consumers.
- [x] The service layer allocates any odd-penny remainder ($\text{total} \pmod N$) to the Payer (if involved) or the first consumer.
- [x] The service saves a `Transaction` and balanced `Entry` records (Payer gets credited, consumers get debited).
- [x] The Group page calculates and displays the net `Balance` for each Participant ($\sum \text{credits} - \sum \text{debits}$).

## Comments
- Implemented pure domain models `TransactionType`, `EntryType`, `EqualSplitCalculator`, `Transaction`, and `Entry` enforcing double-entry invariants ($\sum debits == \sum credits$) and integer minor units.
- Implemented `TransactionRepository` and `EntryRepository` with optimized queries.
- Implemented `TransactionService` supporting equal splits with odd-penny remainder allocation, transaction persistence, and participant net balance derivation.
- Implemented presentation layer: `CreateExpenseForm`, `TransactionController`, `groups/transactions/new.html` expense creation form, and updated `groups/show.html` to display formatted participant net balances and activity log.
- Followed strict bottom-up TDD across pure domain, JPA repositories, service, web controllers, and full Spring Boot integration tests.
- Code-reviewed against project standards and spec; resolved all review findings.
