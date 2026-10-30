package com.valor.commerce;

import com.valor.assets.*;
import com.valor.auth.*;
import com.valor.communication.EmailEventService;
import com.valor.pricing.PricingService;
import com.valor.testsupport.Db37ConnectionCapture;
import com.valor.testsupport.Db37MysqlTestHarness;
import com.valor.testsupport.Db37SpringTestConfiguration;
import com.valor.workflow.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = com.valor.ValorBackendApplication.class, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:db37:forbidden-fallback",
        "spring.datasource.username=db37-fallback-forbidden",
        "spring.datasource.password=db37-fallback-forbidden",
        "spring.flyway.enabled=true",
        "email.enabled=false",
        "smtp.enabled=false",
        "spring.task.scheduling.enabled=false",
        "dev.bootstrap.enabled=false",
        "dev.seed.sample-data-enabled=false"
})
@org.springframework.test.context.ActiveProfiles("mysql-integration")
@Import(Db37SpringTestConfiguration.class)
class Db37WebhookLockEvictionTest {
    @Autowired CommerceService service;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired Db37ConnectionCapture capture;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;

    @MockBean AssetIdentityAccess identities;
    @MockBean CommerceCustomerRepository customers;
    @MockBean CommerceBuildingRepository buildings;
    @MockBean CommerceLiftRepository lifts;
    @MockBean CommerceAmcRepository amcs;
    @MockBean CommerceRequestRepository requests;
    @MockBean CommerceAssignmentRepository assignments;
    @MockBean CommerceReportRepository reports;
    @MockBean PaymentRecordRepository payments;
    @MockBean InvoiceRepository invoices;
    @MockBean CashPaymentOtpRepository cashOtps;
    @MockBean SupportTicketRepository tickets;
    @MockBean AmcRenewalRequestRepository renewals;
    @MockBean AssetDocumentRepository documents;
    @MockBean PaymentRefundRepository refunds;
    @MockBean AssetDocumentStorage storage;
    @MockBean PaymentGateway paymentGateway;
    @MockBean AuditService audit;
    @MockBean EmailEventService emails;
    @MockBean PricingService pricing;

    @AfterEach
    void removeOnlyThisRunWebhookRows() {
        jdbc.update("delete from razorpay_webhook_events where event_id like ?", "db37-eviction-" + Db37MysqlTestHarness.activeRunId() + "-%");
    }

    @Test
    void failedReleaseRetiresPhysicalLockConnection() throws Exception {
        assertSame(dataSource, flyway.getConfiguration().getDataSource());
        ConnectionProvider connectionProvider = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(ConnectionProvider.class);
        assertTrue(connectionProvider instanceof DatasourceConnectionProviderImpl);
        assertSame(dataSource, ((DatasourceConnectionProviderImpl) connectionProvider).getDataSource());
        assertSame(dataSource, ReflectionTestUtils.getField(service, "dataSource"));

        when(paymentGateway.validWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(payments.findByRazorpayOrderId("order-db37")).thenReturn(Optional.empty());

        String eventId = "db37-eviction-" + Db37MysqlTestHarness.activeRunId() + "-" + UUID.randomUUID();
        String lockName = "valor-webhook-" + UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8));
        service.razorpayWebhook(payment(eventId), "sig");

        long originalId = capture.originalConnectionId();
        Connection original = capture.connection();
        assertNotNull(original);
        assertFalse(capture.hasDuplicateEvictionObservation(), "DB37 observed more than one eviction");
        assertEquals(originalId, capture.evictedConnectionId());
        Connection evicted = capture.evictedConnection();
        assertNotNull(evicted);

        try (Connection replacement = dataSource.getConnection()) {
            long replacementId = scalar(replacement, "select connection_id()");
            assertNotEquals(originalId, replacementId);
            assertEquals(1L, scalar(replacement, "select is_free_lock(?)", lockName));
            boolean acquired = scalar(replacement, "select get_lock(?, 0)", lockName) == 1L;
            assertTrue(acquired);
            try {
                if (acquired) assertEquals(0L, scalar(replacement, "select is_free_lock(?)", lockName));
            } finally {
                if (acquired) assertEquals(1L, scalar(replacement, "select release_lock(?)", lockName));
            }
        }

        assertTrue(evicted.isClosed(), "the exact Hikari connection passed to eviction must be closed");
        assertTrue(original.isClosed(), "the original physical lock connection must be closed after eviction");
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from razorpay_webhook_events where event_id=?", Integer.class, eventId));
    }

    private static long scalar(Connection connection, String sql, Object... parameters) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private String payment(String eventId) {
        return "{\"id\":\"" + eventId
                + "\",\"event\":\"payment.authorized\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay-db37\",\"order_id\":\"order-db37\",\"status\":\"authorized\"}}}}";
    }
}
