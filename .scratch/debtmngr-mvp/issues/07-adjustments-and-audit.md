# 07: Adjustments, Edits, and Audit Logs

**What to build:** The Transaction detail view. Unlocked transactions can be edited/soft-deleted (generating an AuditLog). Locked (settled) transactions cannot be edited directly and instead require creating an ADJUSTMENT delta transaction.

**Blocked by:** 06: Intra-Group Settlement & Locking

**Status:** resolved

- [x] Users can click a Transaction to view its details, including all underlying double-entry `Entry` lines.
- [x] If `isLocked = false`, the user can Edit or Delete the transaction.
- [x] Editing/Deleting an unlocked transaction soft-deletes the old record and creates an `AuditLog` entry preserving the history.
- [x] If `isLocked = true`, Edit/Delete is disabled. The user is offered an "Adjust" button.
- [x] Adjusting a locked transaction creates a new `ADJUSTMENT` transaction containing the delta amounts, which links to the original.
