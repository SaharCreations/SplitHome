# Quick API examples

Create a home:
```bash
curl -X POST http://localhost:8080/api/households \
  -H 'Content-Type: application/json' \
  -d '{"name":"Lakewood Apartment"}'
```

Add a member:
```bash
curl -X POST http://localhost:8080/api/households/1/members \
  -H 'Content-Type: application/json' \
  -d '{"name":"Noor"}'
```

Add an expense:
```bash
curl -X POST http://localhost:8080/api/households/1/expenses \
  -H 'Content-Type: application/json' \
  -d '{"description":"Groceries","amount":84,"paidById":1,"participantIds":[1,2,3]}'
```
