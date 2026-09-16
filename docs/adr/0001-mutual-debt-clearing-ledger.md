# 0001: Mutual Debt Clearing Ledger

We use a mutual-debt clearing ledger model where accounts exist only for participants rather than maintaining traditional nominal accounts (such as nominal expense categories or asset accounts).

Transactions directly balance participant credits (amounts paid on behalf of others) against participant debits (portions consumed by others). This minimizes architectural complexity, guarantees $\sum \text{debits} = \sum \text{credits}$ across participants, and allows direct computation of who owes whom without needing a chart-of-accounts hierarchy.
