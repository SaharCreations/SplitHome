package com.splithome.app.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id", nullable = false)
    private Member fromMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_member_id", nullable = false)
    private Member toMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Settlement() {}

    public Settlement(Member fromMember, Member toMember, Household household, BigDecimal amount) {
        this(fromMember, toMember, household, amount, null);
    }

    public Settlement(Member fromMember, Member toMember, Household household, BigDecimal amount, Expense expense) {
        this.fromMember = fromMember;
        this.toMember = toMember;
        this.household = household;
        this.amount = amount;
        this.expense = expense;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getFromMember() { return fromMember; }
    public Member getToMember() { return toMember; }
    public Household getHousehold() { return household; }
    public Expense getExpense() { return expense; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
