package com.projecthk5.orderservice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-closed pricing gate: Finance (TV5) and product owner must approve rates,
 * scale, exemptions before enabling checkout. No default fee policy is baked in.
 */
@Component
public final class MoneyRules {
    private final boolean approved;
    private final BigDecimal rate;
    private final int scale;
    private final Boolean houseExempt;

    public MoneyRules(@Value("${app.finance.policy-approved:false}") boolean approved,
                      @Value("${app.finance.platform-rate:}") String rate,
                      @Value("${app.finance.currency-scale:}") String scale,
                      @Value("${app.finance.house-exempt:}") String houseExempt) {
        this.approved = approved;
        if (approved) {
            if (rate.isBlank() || scale.isBlank() || houseExempt.isBlank())
                throw new IllegalStateException("Approved Finance policy requires explicit rate/scale/exemption");
            try {
                this.rate = new BigDecimal(rate);
                this.scale = Integer.parseInt(scale);
                if (this.rate.signum() < 0 || this.rate.compareTo(BigDecimal.ONE) > 0
                        || this.scale < 0 || this.scale > 4
                        || (!houseExempt.equals("true") && !houseExempt.equals("false")))
                    throw new IllegalArgumentException("Invalid Finance policy");
                this.houseExempt = Boolean.valueOf(houseExempt);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid Finance rate/scale", e);
            }
        } else {
            this.rate = null;
            this.scale = -1;
            this.houseExempt = null;
        }
    }

    public BigDecimal fee(BigDecimal gross, boolean houseStore) {
        if (!approved) throw new IllegalStateException("FINANCE_POLICY_NOT_APPROVED");
        if (gross == null || gross.signum() < 0) throw new IllegalArgumentException("Invalid gross");
        BigDecimal amount = gross.setScale(scale, RoundingMode.UNNECESSARY);
        return amount.multiply(houseStore && houseExempt ? BigDecimal.ZERO : rate)
                .setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || unitPrice.signum() < 0 || quantity < 1)
            throw new IllegalArgumentException("Invalid line item");
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
