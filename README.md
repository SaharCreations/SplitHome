# SplitHome

SplitHome is a focused shared-household expense app built with **Java 17 + Spring Boot**. It answers one question well: **who owes whom, and which specific shared expenses have actually been settled?**

## Why Java / Spring Boot
The app deliberately puts the business rules on the backend: exact-cent splitting, balance calculation, pairwise debt calculation, settlement validation, household isolation, persistence, and paginated expense retrieval. Spring Boot provides a clean REST/service/repository structure and JPA/Hibernate persistence.

## Features
- Multiple independent homes
- Add/remove household members (removal requires a zero balance)
- Required expense name and valid positive money amount
- Equal splitting with deterministic leftover-cent handling
- Live balances and exact pairwise “who owes whom” balances
- Settle a specific person's share of a specific expense
- General payments plus edit/delete corrections
- Delete expenses and safely recalculate balances
- Permanently delete an entire home and its related records
- Server-side expense pagination (20 per request, max 50) and search
- Friendly validation/API errors
- Persistent local H2 database
- Unit tests for split and pairwise debt-netting math

## Architecture
`Controller -> Service (business rules / transactions) -> Spring Data JPA repositories -> H2`

The frontend is plain HTML/CSS/JavaScript and consumes the REST API. DTOs keep persistence entities out of the API contract.

## Run
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run
```
Open `http://localhost:8080`.

## Test
```bash
mvn test
```

## Key API routes
- `GET/POST /api/households`
- `DELETE /api/households/{householdId}`
- `POST /api/households/{householdId}/members`
- `DELETE /api/households/{householdId}/members/{memberId}`
- `GET /api/households/{householdId}/expenses?page=0&size=20&search=`
- `POST /api/households/{householdId}/expenses`
- `DELETE /api/households/{householdId}/expenses/{expenseId}`
- `POST /api/households/{householdId}/expenses/{expenseId}/shares/{memberId}/settle`
- `POST /api/households/{householdId}/settlements`
- `PUT/DELETE /api/households/{householdId}/settlements/{settlementId}`
- `GET /api/households/{householdId}/dashboard`

## Scope decisions
SplitHome intentionally does **not** include bank connections, budgeting, chat, admin roles, or AI. Voice expense entry is a possible later enhancement, but it is not part of the current MVP.
