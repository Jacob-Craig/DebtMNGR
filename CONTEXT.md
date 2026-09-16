# DebtMNGR

A minimalist, double-entry bookkeeping debt manager application for tracking and settling shared expenses.

## Language

**Participant**:
An individual entity with an account in the ledger who can pay for expenses or owe money.
_Avoid_: User, Contact, Debtor, Creditor

**Account**:
A ledger account tracking the net balance of credits and debits for a specific Participant within a Group.
_Avoid_: Bank account, Profile

**Transaction**:
A recorded financial event with a timestamp and description that transfers value, composed of balanced entries where total debits equal total credits.
_Avoid_: Bill, Record, Payment (when referring to an expense)

**Entry**:
A single debit or credit line item belonging to a Transaction that mutates an Account balance.
_Avoid_: Split, Line item, Portion

**Group**:
A named container of Participants and Transactions (e.g., "Apartment", "Holiday") that scopes shared expenses and balances.
_Avoid_: Workspace, Folder, Project

**Category**:
A managed classification entity (e.g., "Groceries", "Utilities") with a curated set of system defaults and optional custom entries, linked to Transactions for filtering and reporting.
_Avoid_: Tag, Label, Type


**Payer**:
The single Participant who paid the total amount upfront for a Transaction.
_Avoid_: Purchaser, Buyer, Spender

**Settlement**:
A Transaction whose sole purpose is to pay down or eliminate an outstanding debt between two Participants.
_Avoid_: Repayment, Transfer, Payoff

**Balance**:
The net position of a Participant derived as total credits minus total debits ($\sum \text{Credits} - \sum \text{Debits}$). A positive balance indicates the Participant is owed money; a negative balance indicates the Participant owes money.
_Avoid_: Total, Remaining, Amount due

**AuditLog**:
An immutable record capturing a change (edit or deletion) to a Transaction, preserving the prior state and timestamp.
_Avoid_: History, Changelog, Event

**Adjustment**:
A Transaction linked to an existing locked Transaction to record additional amounts or corrections without mutating settled history.
_Avoid_: Patch, Delta, Correction, Edit

