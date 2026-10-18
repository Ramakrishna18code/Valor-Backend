package com.valor.installation;

import java.math.BigDecimal;
import java.time.LocalDate;

final class InstallationMilestoneInvoiceDtos {
    record View(Long milestoneId, Long invoiceId, Long installationRequestId, BigDecimal amount, BigDecimal taxAmount, BigDecimal totalAmount, String currency, String status, LocalDate issuedDate) {}
}
