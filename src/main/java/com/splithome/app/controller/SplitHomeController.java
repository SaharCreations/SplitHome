package com.splithome.app.controller;

import com.splithome.app.dto.Requests.*;
import com.splithome.app.dto.Responses.*;
import com.splithome.app.service.SplitHomeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SplitHomeController {
    private final SplitHomeService service;
    public SplitHomeController(SplitHomeService service) { this.service = service; }

    @GetMapping("/households") public List<HouseholdDto> households() { return service.households(); }
    @PostMapping("/households") @ResponseStatus(HttpStatus.CREATED) public HouseholdDto createHousehold(@Valid @RequestBody HouseholdRequest request) { return service.createHousehold(request); }
    @DeleteMapping("/households/{householdId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteHousehold(@PathVariable Long householdId) { service.deleteHousehold(householdId); }
    @PostMapping("/households/{householdId}/members") @ResponseStatus(HttpStatus.CREATED) public MemberDto addMember(@PathVariable Long householdId, @Valid @RequestBody MemberRequest request) { return service.addMember(householdId, request); }
    @DeleteMapping("/households/{householdId}/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeMember(@PathVariable Long householdId, @PathVariable Long memberId) { service.removeMember(householdId, memberId); }
    @PostMapping("/households/{householdId}/expenses") @ResponseStatus(HttpStatus.CREATED) public ExpenseDto addExpense(@PathVariable Long householdId, @Valid @RequestBody ExpenseRequest request) { return service.addExpense(householdId, request); }
    @GetMapping("/households/{householdId}/expenses") public ExpensePageDto expenses(@PathVariable Long householdId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String search) { return service.expenses(householdId, page, size, search); }
    @DeleteMapping("/households/{householdId}/expenses/{expenseId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteExpense(@PathVariable Long householdId, @PathVariable Long expenseId) { service.deleteExpense(householdId, expenseId); }
    @PostMapping("/households/{householdId}/expenses/{expenseId}/shares/{memberId}/settle") @ResponseStatus(HttpStatus.CREATED) public SettlementDto settleExpenseShare(@PathVariable Long householdId, @PathVariable Long expenseId, @PathVariable Long memberId) { return service.settleExpenseShare(householdId, expenseId, memberId); }
    @PostMapping("/households/{householdId}/settlements") @ResponseStatus(HttpStatus.CREATED) public SettlementDto settle(@PathVariable Long householdId, @Valid @RequestBody SettlementRequest request) { return service.settle(householdId, request); }
    @PutMapping("/households/{householdId}/settlements/{settlementId}") public SettlementDto updateSettlement(@PathVariable Long householdId, @PathVariable Long settlementId, @Valid @RequestBody SettlementUpdateRequest request) { return service.updateSettlement(householdId, settlementId, request); }
    @DeleteMapping("/households/{householdId}/settlements/{settlementId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSettlement(@PathVariable Long householdId, @PathVariable Long settlementId) { service.deleteSettlement(householdId, settlementId); }
    @GetMapping("/households/{householdId}/dashboard") public DashboardDto dashboard(@PathVariable Long householdId) { return service.dashboard(householdId); }
}
