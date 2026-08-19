# SplitHome QA checklist

Before a portfolio release, verify these flows manually in addition to `mvn test`.

## Money and validation
- Expense name blank -> visible dialog error
- Amount blank, letters, 0, negative, >2 decimals, huge value -> rejected cleanly
- $10 / 3 -> $3.34 + $3.33 + $3.33
- General payment cannot exceed the current suggested debt
- Specific share cannot be settled twice
- Editing an item-linked payment cannot exceed that share

## Data integrity
- Create two homes; members/expenses/payments never appear in the other home
- Delete an expense with item-linked payments -> related payments disappear and balances recalculate
- Edit/delete a payment -> balances recalculate
- Member with non-zero balance cannot be removed
- Removing a zero-balance member preserves historical names
- Delete a home -> all of that home's members, expenses, shares, and payments are deleted; other homes remain

## Pagination / UI
- Create >20 expenses -> only 20 fetched initially
- Show more -> next page is fetched
- Search -> server returns matching expense descriptions
- Errors stay visible inside dialogs
- Empty home and no-search-results states render cleanly
