package com.valor.installation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:milestone_schema_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class InstallationMilestoneSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void v50CreatesTheMilestoneFoundationWithDatabaseConstraints() {
        assertEquals(1, count("select count(*) from information_schema.tables where table_name = 'INSTALLATION_MILESTONES'"));
        assertEquals(1, count("select count(*) from information_schema.table_constraints where table_name = 'INSTALLATION_MILESTONES' and constraint_name = 'UK_INSTALLATION_MILESTONE_CODE' and constraint_type = 'UNIQUE'"));

        assertEquals(1, count("select count(*) from information_schema.table_constraints where table_name = 'INSTALLATION_MILESTONES' and constraint_name = 'FK_INSTALLATION_MILESTONE_REQUEST' and constraint_type = 'FOREIGN KEY'"));
        assertEquals(1, count("select count(*) from information_schema.table_constraints where table_name = 'INSTALLATION_MILESTONES' and constraint_name = 'FK_INSTALLATION_MILESTONE_INVOICE' and constraint_type = 'FOREIGN KEY'"));
        assertEquals(1, count("select count(*) from information_schema.table_constraints where table_name = 'INSTALLATION_MILESTONES' and constraint_name = 'FK_INSTALLATION_MILESTONE_PAYMENT' and constraint_type = 'FOREIGN KEY'"));

        assertEquals(1, nullable("INSTALLATION_REQUEST_ID"));
        assertEquals(1, nullable("MILESTONE_CODE"));
        assertEquals(1, nullable("SEQUENCE_NO"));
        assertEquals(1, nullable("STATUS"));
        assertEquals(1, nullable("VERSION"));
        assertEquals(1, nullable("CREATED_AT"));
        assertEquals(1, nullable("UPDATED_AT"));
    }

    @Test
    void v50RestrictsStatusesAndPositiveAmountsWithoutEnablingBilling() {
        assertEquals(1, count("select count(*) from information_schema.check_constraints where constraint_name = 'CK_INSTALLATION_MILESTONE_STATUS'"));
        assertEquals(1, count("select count(*) from information_schema.check_constraints where constraint_name = 'CK_INSTALLATION_MILESTONE_AMOUNT'"));
        assertEquals(1, count("select count(*) from information_schema.indexes where table_name = 'INSTALLATION_MILESTONES' and index_name = 'IDX_INSTALLATION_MILESTONE_REQUEST_STATUS'"));
        assertEquals(1, count("select count(*) from information_schema.indexes where table_name = 'INVOICES' and index_name = 'UK_INVOICE_INSTALLATION_MILESTONE'"));
    }

    @Test
    void databaseRejectsMilestoneRowsForUnknownInstallations() {
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("insert into installation_milestones(installation_request_id,milestone_code,sequence_no,status,version,created_at,updated_at) values (?,?,?,?,?,?,?)", 999999L, "UNKNOWN", 1, "PLANNED", 0L, java.time.LocalDateTime.now(), java.time.LocalDateTime.now()));
    }

    @Test
    void v54RejectsDuplicateMilestoneInvoiceAndAllowsValidNullMilestoneInvoices() {
        String email = "milestone-" + UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users(email,role) values (?,?)", email, "CUSTOMER");
        Long userId = jdbc.queryForObject("select id from users where email=?", Long.class, email);
        jdbc.update("insert into customer_profiles(user_id,full_name) values (?,?)", userId, "Milestone Test Customer");
        Long customerId = jdbc.queryForObject("select id from customer_profiles where user_id=?", Long.class, userId);
        jdbc.update("insert into installation_requests(customer_id,status,version,created_at,updated_at) values (?,?,?,?,?)", customerId, "COMPLETED", 0L, LocalDateTime.now(), LocalDateTime.now());
        Long requestId = jdbc.queryForObject("select id from installation_requests where customer_id=? order by id desc limit 1", Long.class, customerId);
        jdbc.update("insert into installation_milestones(installation_request_id,milestone_code,sequence_no,status,version,created_at,updated_at) values (?,?,?,?,?,?,?)", requestId, "FINAL", 1, "INVOICED", 0L, LocalDateTime.now(), LocalDateTime.now());
        Long milestoneId = jdbc.queryForObject("select id from installation_milestones where installation_request_id=?", Long.class, requestId);
        insertInvoice(customerId, userId, "MILESTONE-1", "ISSUED", milestoneId);
        assertThrows(DataIntegrityViolationException.class, () -> insertInvoice(customerId, userId, "MILESTONE-2", "ISSUED", milestoneId));
        insertInvoice(customerId, userId, "SERVICE-NULL-1", "VOID", null);
        insertInvoice(customerId, userId, "AMC-NULL-1", "CANCELLED", null);
    }

    private void insertInvoice(Long customerId, Long actorId, String number, String status, Long milestoneId) {
        jdbc.update("insert into invoices(invoice_number,customer_id,created_by_user_id,description,subtotal,tax_amount,total_amount,currency,status,issued_date,installation_milestone_id) values (?,?,?,?,?,?,?,?,?,?,?)", number, customerId, actorId, "Schema test invoice", 100.00, 0.00, 100.00, "INR", status, LocalDate.now(), milestoneId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private int nullable(String column) {
        return jdbc.queryForObject(
            "select count(*) from information_schema.columns where table_name = 'INSTALLATION_MILESTONES' and column_name = ? and is_nullable = 'NO'",
            Integer.class, column);
    }
}
