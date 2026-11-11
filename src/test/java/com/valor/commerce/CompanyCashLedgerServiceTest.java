package com.valor.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.Role;
import com.valor.auth.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class CompanyCashLedgerServiceTest {
    @Test
    void remittanceIsIdempotentCannotExceedCollectedCashAndNeedsAdminVerification() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:company_cash_ledger;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE technician_profiles (id BIGINT PRIMARY KEY, user_id BIGINT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE company_cash_receipts (id BIGINT PRIMARY KEY, technician_profile_id BIGINT, amount DECIMAL(12,2))");
        jdbc.execute("CREATE TABLE company_cash_remittances (id BIGINT AUTO_INCREMENT PRIMARY KEY, technician_profile_id BIGINT, idempotency_key VARCHAR(80), claimed_amount DECIMAL(12,2), currency VARCHAR(3), method VARCHAR(30), handover_reference VARCHAR(120), status VARCHAR(30) DEFAULT 'PENDING_VERIFICATION', submitted_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6), verified_at DATETIME(6), verified_by_user_id BIGINT, verification_reason VARCHAR(1000), UNIQUE(technician_profile_id,idempotency_key))");
        jdbc.execute("CREATE TABLE company_cash_remittance_items (id BIGINT AUTO_INCREMENT PRIMARY KEY, remittance_id BIGINT, receipt_id BIGINT, amount DECIMAL(12,2))");
        jdbc.update("INSERT INTO technician_profiles VALUES (?,?,TRUE)", 7L, 17L);
        jdbc.update("INSERT INTO company_cash_receipts VALUES (?,?,?)", 20L, 7L, new BigDecimal("6000.00"));
        var identity = mock(AssetIdentityAccess.class);
        var technician = new User(); ReflectionTestUtils.setField(technician, "id", 17L); technician.setRole(Role.TECHNICIAN);
        when(identity.actor()).thenReturn(technician);
        var service = new CompanyCashLedgerService(jdbc, identity);

        var request = new CompanyCashLedgerService.Submit("cash-test-key-1", new BigDecimal("4000.00"), "OFFICE_HANDOVER", "OFFICE-123");
        var first = service.submit(request);
        assertEquals("PENDING_VERIFICATION", first.status());
        assertEquals(first.id(), service.submit(request).id());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM company_cash_remittances", Integer.class));
        assertThrows(CommerceException.class, () -> service.submit(new CompanyCashLedgerService.Submit("cash-test-key-1", new BigDecimal("3000.00"), "OFFICE_HANDOVER", "OFFICE-123")));
        assertThrows(CommerceException.class, () -> service.submit(new CompanyCashLedgerService.Submit("cash-test-key-2", new BigDecimal("2000.01"), "BANK_DEPOSIT", "BANK-456")));

        var admin = new User(); ReflectionTestUtils.setField(admin, "id", 99L); admin.setRole(Role.ADMIN);
        when(identity.actor()).thenReturn(admin);
        var reviewed = service.review(first.id(), new CompanyCashLedgerService.Review(true, "Office confirmed cash handover"));
        assertEquals("VERIFIED", reviewed.status());
        assertThrows(CommerceException.class, () -> service.review(first.id(), new CompanyCashLedgerService.Review(true, "Again")));
        when(identity.actor()).thenReturn(technician);
        var summary = service.summary();
        assertEquals(new BigDecimal("6000.00"), summary.collected());
        assertEquals(new BigDecimal("4000.00"), summary.remitted());
        assertEquals(new BigDecimal("2000.00"), summary.outstanding());
    }
}
