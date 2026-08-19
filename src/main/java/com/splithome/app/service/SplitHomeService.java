package com.splithome.app.service;

import com.splithome.app.dto.Requests.*;
import com.splithome.app.dto.Responses.*;
import com.splithome.app.model.*;
import com.splithome.app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SplitHomeService {
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository shareRepository;
    private final SettlementRepository settlementRepository;

    public SplitHomeService(HouseholdRepository householdRepository,
                            MemberRepository memberRepository,
                            ExpenseRepository expenseRepository,
                            ExpenseShareRepository shareRepository,
                            SettlementRepository settlementRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.settlementRepository = settlementRepository;
    }

    public List<HouseholdDto> households() {
        return householdRepository.findAll().stream()
                .map(h -> new HouseholdDto(h.getId(), h.getName()))
                .toList();
    }

    public HouseholdDto createHousehold(HouseholdRequest request) {
        String name = request.name().trim();
        Household h = householdRepository.save(new Household(name));
        return new HouseholdDto(h.getId(), h.getName());
    }

    @Transactional
    public void deleteHousehold(Long householdId) {
        Household h = household(householdId);
        List<Expense> expenses = expenseRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId);
        settlementRepository.deleteAll(settlementRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId));
        shareRepository.deleteAll(shareRepository.findByExpenseHouseholdId(householdId));
        expenseRepository.deleteAll(expenses);
        memberRepository.deleteAll(memberRepository.findByHouseholdIdOrderByIdAsc(householdId));
        householdRepository.delete(h);
    }

    public MemberDto addMember(Long householdId, MemberRequest request) {
        Household h = household(householdId);
        String name = request.name().trim();
        if (memberRepository.existsByHouseholdIdAndActiveTrueAndNameIgnoreCase(householdId, name)) {
            throw new IllegalArgumentException("A person with that name is already in this home.");
        }
        Member m = memberRepository.save(new Member(name, h));
        return new MemberDto(m.getId(), m.getName());
    }


    @Transactional
    public void removeMember(Long householdId, Long memberId) {
        Member member = memberInHousehold(memberId, householdId);
        BigDecimal balance = memberBalance(householdId, memberId);
        if (balance.abs().compareTo(new BigDecimal("0.009")) > 0) {
            throw new IllegalArgumentException("Settle " + member.getName() + "'s balance before removing them from this home.");
        }
        member.setActive(false);
        memberRepository.save(member);
    }

    @Transactional
    public ExpenseDto addExpense(Long householdId, ExpenseRequest request) {
        Household h = household(householdId);
        Member payer = memberInHousehold(request.paidById(), householdId);
        List<Long> participantIds = request.participantIds().stream().distinct().toList();
        if (participantIds.isEmpty()) throw new IllegalArgumentException("Select at least one participant.");

        List<Member> participants = participantIds.stream()
                .map(id -> memberInHousehold(id, householdId))
                .toList();

        BigDecimal amount = money(request.amount());
        Expense expense = expenseRepository.save(new Expense(request.description().trim(), amount, payer, h));

        List<BigDecimal> shares = equalShares(amount, participants.size());
        for (int i = 0; i < participants.size(); i++) {
            shareRepository.save(new ExpenseShare(expense, participants.get(i), shares.get(i)));
        }
        return expenseDto(expense);
    }

    @Transactional
    public ExpensePageDto expenses(Long householdId, int page, int size, String search) {
        household(householdId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String q = search == null ? "" : search.trim();
        Page<Expense> result = q.isEmpty()
                ? expenseRepository.findByHouseholdId(householdId, pageable)
                : expenseRepository.findByHouseholdIdAndDescriptionContainingIgnoreCase(householdId, q, pageable);
        return new ExpensePageDto(result.getContent().stream().map(this::expenseDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional
    public void deleteExpense(Long householdId, Long expenseId) {
        Expense expense = expenseInHousehold(expenseId, householdId);
        settlementRepository.deleteByExpenseId(expenseId);
        shareRepository.deleteByExpenseId(expenseId);
        expenseRepository.delete(expense);
    }

    @Transactional
    public SettlementDto settle(Long householdId, SettlementRequest request) {
        if (request.fromMemberId().equals(request.toMemberId())) {
            throw new IllegalArgumentException("Payer and receiver must be different people.");
        }
        Household h = household(householdId);
        Member from = memberInHousehold(request.fromMemberId(), householdId);
        Member to = memberInHousehold(request.toMemberId(), householdId);
        BigDecimal amount = money(request.amount());
        BigDecimal owed = currentDebtBetween(householdId, from.getId(), to.getId());
        if (owed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(from.getName() + " does not currently owe " + to.getName() + ".");
        }
        if (amount.compareTo(owed) > 0) {
            throw new IllegalArgumentException("Payment cannot exceed the current balance of " + owed + ".");
        }
        Settlement s = settlementRepository.save(new Settlement(from, to, h, amount));
        return settlementDto(s);
    }

    @Transactional
    public SettlementDto settleExpenseShare(Long householdId, Long expenseId, Long memberId) {
        Household h = household(householdId);
        Expense expense = expenseInHousehold(expenseId, householdId);
        Member payer = expense.getPaidBy();
        Member debtor = memberInHousehold(memberId, householdId);

        if (debtor.getId().equals(payer.getId())) {
            throw new IllegalArgumentException("The person who paid does not owe themselves for this expense.");
        }

        ExpenseShare share = shareRepository.findByExpenseIdAndMemberId(expenseId, memberId)
                .orElseThrow(() -> new EntityNotFoundException("That person is not part of this expense."));

        BigDecimal alreadyPaid = itemSettledAmount(expenseId, debtor.getId(), payer.getId());
        BigDecimal remaining = money(share.getAmount().subtract(alreadyPaid));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("This person's share is already settled.");
        }

        Settlement settlement = settlementRepository.save(new Settlement(debtor, payer, h, remaining, expense));
        return settlementDto(settlement);
    }

    @Transactional
    public SettlementDto updateSettlement(Long householdId, Long settlementId, SettlementUpdateRequest request) {
        Settlement settlement = settlementInHousehold(settlementId, householdId);
        BigDecimal newAmount = money(request.amount());

        if (settlement.getExpense() == null) {
            BigDecimal currentlyOwedAfterOldPayment = currentDebtBetween(householdId, settlement.getFromMember().getId(), settlement.getToMember().getId());
            BigDecimal maxAllowed = money(currentlyOwedAfterOldPayment.add(settlement.getAmount()));
            if (newAmount.compareTo(maxAllowed) > 0) {
                throw new IllegalArgumentException("Payment cannot exceed the available balance of " + maxAllowed + ".");
            }
        } else {
            ExpenseShare share = shareRepository.findByExpenseIdAndMemberId(
                            settlement.getExpense().getId(), settlement.getFromMember().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Expense share not found."));

            BigDecimal otherPayments = settlementRepository.findByExpenseId(settlement.getExpense().getId()).stream()
                    .filter(s -> !s.getId().equals(settlementId))
                    .filter(s -> s.getFromMember().getId().equals(settlement.getFromMember().getId()))
                    .filter(s -> s.getToMember().getId().equals(settlement.getToMember().getId()))
                    .map(Settlement::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal maxAllowed = money(share.getAmount().subtract(otherPayments));
            if (newAmount.compareTo(maxAllowed) > 0) {
                throw new IllegalArgumentException("This payment cannot exceed the remaining share of " + maxAllowed + ".");
            }
        }

        settlement.setAmount(newAmount);
        return settlementDto(settlementRepository.save(settlement));
    }

    @Transactional
    public void deleteSettlement(Long householdId, Long settlementId) {
        Settlement settlement = settlementInHousehold(settlementId, householdId);
        settlementRepository.delete(settlement);
    }

    @Transactional
    public DashboardDto dashboard(Long householdId) {
        Household h = household(householdId);
        List<Member> members = memberRepository.findByHouseholdIdAndActiveTrueOrderByIdAsc(householdId);
        List<Expense> expenses = expenseRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId);
        List<Settlement> settlements = settlementRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId);
        List<ExpenseShare> shares = shareRepository.findByExpenseHouseholdId(householdId);

        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        members.forEach(m -> balances.put(m.getId(), BigDecimal.ZERO.setScale(2)));

        for (Expense e : expenses) {
            balances.computeIfPresent(e.getPaidBy().getId(), (id, v) -> v.add(e.getAmount()));
        }
        for (ExpenseShare share : shares) {
            balances.computeIfPresent(share.getMember().getId(), (id, v) -> v.subtract(share.getAmount()));
        }
        for (Settlement s : settlements) {
            balances.computeIfPresent(s.getFromMember().getId(), (id, v) -> v.add(s.getAmount()));
            balances.computeIfPresent(s.getToMember().getId(), (id, v) -> v.subtract(s.getAmount()));
        }

        Map<Long, Member> memberMap = members.stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        List<BalanceDto> balanceDtos = balances.entrySet().stream()
                .map(e -> new BalanceDto(e.getKey(), memberMap.get(e.getKey()).getName(), money(e.getValue())))
                .toList();

        List<DebtDto> debts = simplifyDebts(balanceDtos);
        BigDecimal totalSpent = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardDto(
                new HouseholdDto(h.getId(), h.getName()),
                members.stream().map(m -> new MemberDto(m.getId(), m.getName())).toList(),
                settlements.stream().map(this::settlementDto).toList(),
                balanceDtos,
                debts,
                money(totalSpent)
        );
    }

    public List<BigDecimal> equalShares(BigDecimal total, int count) {
        if (count <= 0) throw new IllegalArgumentException("Participant count must be positive.");
        long cents = money(total).movePointRight(2).longValueExact();
        long base = cents / count;
        long remainder = cents % count;
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long shareCents = base + (i < remainder ? 1 : 0);
            result.add(BigDecimal.valueOf(shareCents, 2));
        }
        return result;
    }

    public List<DebtDto> simplifyDebts(List<BalanceDto> balances) {
        record Node(Long id, String name, long cents) {}
        List<Node> creditors = new ArrayList<>();
        List<Node> debtors = new ArrayList<>();

        for (BalanceDto b : balances) {
            long cents = money(b.balance()).movePointRight(2).longValue();
            if (cents > 0) creditors.add(new Node(b.memberId(), b.memberName(), cents));
            if (cents < 0) debtors.add(new Node(b.memberId(), b.memberName(), -cents));
        }

        creditors.sort(Comparator.comparingLong(Node::cents).reversed());
        debtors.sort(Comparator.comparingLong(Node::cents).reversed());
        List<DebtDto> result = new ArrayList<>();
        int ci = 0, di = 0;

        while (ci < creditors.size() && di < debtors.size()) {
            Node c = creditors.get(ci);
            Node d = debtors.get(di);
            long paid = Math.min(c.cents(), d.cents());
            if (paid > 0) {
                result.add(new DebtDto(d.id(), d.name(), c.id(), c.name(), BigDecimal.valueOf(paid, 2)));
            }
            long cLeft = c.cents() - paid;
            long dLeft = d.cents() - paid;
            creditors.set(ci, new Node(c.id(), c.name(), cLeft));
            debtors.set(di, new Node(d.id(), d.name(), dLeft));
            if (cLeft == 0) ci++;
            if (dLeft == 0) di++;
        }
        return result;
    }

    private ExpenseDto expenseDto(Expense e) {
        List<Settlement> itemPayments = settlementRepository.findByExpenseId(e.getId());
        List<ShareDto> shares = shareRepository.findByExpenseId(e.getId()).stream()
                .map(s -> {
                    if (s.getMember().getId().equals(e.getPaidBy().getId())) {
                        return new ShareDto(s.getMember().getId(), s.getMember().getName(), money(s.getAmount()),
                                money(s.getAmount()), BigDecimal.ZERO.setScale(2), true);
                    }
                    BigDecimal settled = itemPayments.stream()
                            .filter(p -> p.getFromMember().getId().equals(s.getMember().getId()))
                            .filter(p -> p.getToMember().getId().equals(e.getPaidBy().getId()))
                            .map(Settlement::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal remaining = money(s.getAmount().subtract(settled).max(BigDecimal.ZERO));
                    return new ShareDto(s.getMember().getId(), s.getMember().getName(), money(s.getAmount()),
                            money(settled), remaining, remaining.compareTo(BigDecimal.ZERO) == 0);
                })
                .toList();
        return new ExpenseDto(e.getId(), e.getDescription(), e.getAmount(), e.getPaidBy().getId(),
                e.getPaidBy().getName(), e.getCreatedAt(), shares);
    }

    private SettlementDto settlementDto(Settlement s) {
        Long expenseId = s.getExpense() == null ? null : s.getExpense().getId();
        String expenseDescription = s.getExpense() == null ? null : s.getExpense().getDescription();
        return new SettlementDto(s.getId(), s.getFromMember().getId(), s.getFromMember().getName(),
                s.getToMember().getId(), s.getToMember().getName(), s.getAmount(), s.getCreatedAt(),
                expenseId, expenseDescription);
    }

    private BigDecimal itemSettledAmount(Long expenseId, Long fromMemberId, Long toMemberId) {
        return money(settlementRepository.findByExpenseId(expenseId).stream()
                .filter(s -> s.getFromMember().getId().equals(fromMemberId))
                .filter(s -> s.getToMember().getId().equals(toMemberId))
                .map(Settlement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }


    private BigDecimal currentDebtBetween(Long householdId, Long fromMemberId, Long toMemberId) {
        DashboardDto d = dashboard(householdId);
        return d.debts().stream()
                .filter(x -> x.fromMemberId().equals(fromMemberId) && x.toMemberId().equals(toMemberId))
                .map(DebtDto::amount).findFirst().orElse(BigDecimal.ZERO.setScale(2));
    }

    private BigDecimal memberBalance(Long householdId, Long memberId) {
        BigDecimal balance = BigDecimal.ZERO.setScale(2);
        for (Expense expense : expenseRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId)) {
            if (expense.getPaidBy().getId().equals(memberId)) {
                balance = balance.add(expense.getAmount());
            }
        }
        for (ExpenseShare share : shareRepository.findByExpenseHouseholdId(householdId)) {
            if (share.getMember().getId().equals(memberId)) {
                balance = balance.subtract(share.getAmount());
            }
        }
        for (Settlement settlement : settlementRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId)) {
            if (settlement.getFromMember().getId().equals(memberId)) {
                balance = balance.add(settlement.getAmount());
            }
            if (settlement.getToMember().getId().equals(memberId)) {
                balance = balance.subtract(settlement.getAmount());
            }
        }
        return money(balance);
    }

    private Household household(Long id) {
        return householdRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Household not found."));
    }

    private Expense expenseInHousehold(Long expenseId, Long householdId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Expense not found."));
        if (!expense.getHousehold().getId().equals(householdId)) {
            throw new IllegalArgumentException("Expense does not belong to this household.");
        }
        return expense;
    }

    private Settlement settlementInHousehold(Long settlementId, Long householdId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found."));
        if (!settlement.getHousehold().getId().equals(householdId)) {
            throw new IllegalArgumentException("Payment does not belong to this household.");
        }
        return settlement;
    }

    private Member memberInHousehold(Long id, Long householdId) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Member not found."));
        if (!m.getHousehold().getId().equals(householdId)) {
            throw new IllegalArgumentException("Member does not belong to this household.");
        }
        if (!m.isActive()) {
            throw new IllegalArgumentException("This person has been removed from the household.");
        }
        return m;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
