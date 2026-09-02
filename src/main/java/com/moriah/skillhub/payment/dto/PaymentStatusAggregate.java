package com.moriah.skillhub.payment.dto;

import com.moriah.skillhub.payment.entity.PaymentStatus;

import java.math.BigDecimal;

/** Spring Data interface projection for {@code PaymentRepository.summarizeByStatus} — one row
 * per {@link PaymentStatus} with its count and summed amount. */
public interface PaymentStatusAggregate {

    PaymentStatus getStatus();

    long getCount();

    BigDecimal getTotalAmount();
}
