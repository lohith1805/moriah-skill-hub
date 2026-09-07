package com.moriah.skillhub.payment.repository;

import com.moriah.skillhub.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPaymentId(Long paymentId);

    /** Billing-history read ({@code GET /subscriptions/me/invoices}) — one query for every
     * invoice behind the caller's captured payments, {@code payment} join-fetched so building
     * {@code InvoiceResponse} does not N+1 on {@code invoice.payment.id}. */
    @Query("SELECT i FROM Invoice i JOIN FETCH i.payment p WHERE p.id IN :paymentIds")
    List<Invoice> findByPaymentIdIn(@Param("paymentIds") Collection<Long> paymentIds);
}
