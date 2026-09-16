# 03: Equal-Split Expense Recording & Balances

**What to build:** The core double-entry accounting engine. Users can record a new Expense (Equal Split only). The system creates a Transaction with balanced debit/credit Entries, handles odd-penny remainders, and displays each Participant's net Balance on the Group page.

**Blocked by:** 02: Group Roster & Participants

**Status:** ready-for-agent

- [ ] A user can open an Expense form in the Group, specifying a description, amount, Payer, and selected consumers.
- [ ] The service layer divides the amount equally among consumers.
- [ ] The service layer allocates any odd-penny remainder ($\text{total} \pmod N$) to the Payer (if involved) or the first consumer.
- [ ] The service saves a `Transaction` and balanced `Entry` records (Payer gets credited, consumers get debited).
- [ ] The Group page calculates and displays the net `Balance` for each Participant ($\sum \text{credits} - \sum \text{debits}$).
