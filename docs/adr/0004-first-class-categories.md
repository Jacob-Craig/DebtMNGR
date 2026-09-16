# 0004: First-Class Category Entities with System Defaults

Categories are modeled as first-class entities with a foreign key relationship to Transactions, rather than freeform strings.

To prevent bloat, misspellings, and internationalization challenges:
1. The system provides a seed list of canonical **System Categories** identified by unique keys (e.g. `GROCERIES`, `UTILITIES`, `DINING_OUT`). Display names for system categories resolve through Spring i18n message bundles for effortless translation.
2. Custom categories created by users are scoped to their Group, preventing typo pollution across unrelated groups.
3. Because Transactions reference the category via a foreign key, categories can be renamed or merged with zero data loss or migration overhead on transaction history.
