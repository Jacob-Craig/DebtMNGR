# 0002: Reconciliation Locking and Adjustment Transactions

Transactions that have been settled are locked against direct mutation to prevent historical debt corruption.

When a Settlement is recorded between participants, involved prior Transactions are marked locked. If an expense must later be increased or corrected after settlement, rather than mutating the locked record, an **Adjustment** Transaction is created linking back to the original. This ensures continuous auditability and double-entry mathematical integrity without retroactively altering settled states.
