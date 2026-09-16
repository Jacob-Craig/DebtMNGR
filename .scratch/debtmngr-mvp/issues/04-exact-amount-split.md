# 04: Exact Amount Split Mode

**What to build:** Adds the "Exact Amounts" mode to the expense form, allowing users to specify uneven splits with strict sum validation.

**Blocked by:** 03: Equal-Split Expense Recording & Balances

**Status:** ready-for-agent

- [ ] The Expense form allows toggling between "Equal" and "Exact" split modes.
- [ ] In "Exact" mode, the user inputs precise currency amounts for each selected consumer.
- [ ] The service layer strictly validates that the sum of the exact amounts equals the total Transaction amount before saving.
- [ ] If invalid, the form displays a validation error preventing submission.
- [ ] If valid, balanced Entries are saved according to the custom amounts.
