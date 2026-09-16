# 08: Dashboard Global Netting & Cross-Group Settlement

**What to build:** The main Dashboard shows a consolidated global net balance across all shared groups for the operator. Users can trigger a global "Settle Up" which allocates proportional settlement transactions into each group's ledger.

**Blocked by:** 06: Intra-Group Settlement & Locking

**Status:** ready-for-agent

- [ ] The Dashboard aggregates the operator's (`isSelf = true`) net Balance across all Groups.
- [ ] The Dashboard lists a cross-group breakdown (e.g. "Bob owes you £15 total").
- [ ] A global "Settle Up" button allows the operator to record a lump-sum payment with a specific contact (e.g. Bob).
- [ ] The service layer automatically allocates the global settlement amount into proportional `SETTLEMENT` transactions within each Group where a debt exists between the two participants.
- [ ] The cross-group breakdown and individual Group ledgers correctly reflect the settled amounts.
