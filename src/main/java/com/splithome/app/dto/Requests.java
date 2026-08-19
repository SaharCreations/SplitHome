package com.splithome.app.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class Requests {
    private Requests() {}

    public record HouseholdRequest(@NotBlank @Size(max = 100) String name) {}
    public record MemberRequest(@NotBlank @Size(max = 80) String name) {}

    public record ExpenseRequest(
            @NotBlank @Size(max = 120) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull Long paidById,
            @NotEmpty List<Long> participantIds
    ) {}

    public record SettlementRequest(
            @NotNull Long fromMemberId,
            @NotNull Long toMemberId,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount
    ) {}

    public record SettlementUpdateRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal amount
    ) {}
}
