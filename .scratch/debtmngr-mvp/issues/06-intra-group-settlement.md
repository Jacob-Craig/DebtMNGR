# 06: Intra-Group Settlement & Locking

**What to build:** Users can click "Settle Up" inside a Group. This creates a SETTLEMENT transaction, clears the debt between two participants, and marks their prior unsettled transactions in that group as locked.

**Blocked by:** 03: Equal-Split Expense Recording & Balances

**Status:** ready-for-agent

- [ ] A "Settle Up" modal/page in the Group allows selecting a Payer, a Receiver, and an amount.
- [ ] Submitting the form saves a `Transaction` of type `SETTLEMENT` (crediting the Payer, debiting the Receiver).
- [ ] Upon saving, the service identifies all prior unlocked transactions involving both participants in that Group and marks them `isLocked = true`.
- [ ] The dashboard and group page accurately reflect the new zeroed or reduced balances.
