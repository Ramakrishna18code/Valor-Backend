package com.valor.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServiceRequestIdMigrationTest {
    @Test
    void changesOldReferencesAndKeepsTheirLookupAliases() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:service_request_id_migration;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            try (var statement = connection.createStatement()) {
                statement.execute("create table service_requests (id bigint primary key, lift_id bigint, service_id varchar(80) not null unique)");
                statement.execute("insert into service_requests values (42, 15, 'SR-96a3c5a4-0c7d-4f0e-bec1-d3fce14259ba')");
                statement.execute("insert into service_requests values (43, null, 'ANANYA-SR-2026-001')");
                statement.execute("insert into service_requests values (44, 15, 'SR-LFT-15-44')");
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V77__normalize_service_request_references.sql"));

            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("select service_id, legacy_service_id from service_requests order by id")) {
                rows.next();
                assertEquals("SR-LFT-15-42", rows.getString(1));
                assertEquals("SR-96a3c5a4-0c7d-4f0e-bec1-d3fce14259ba", rows.getString(2));
                rows.next();
                assertEquals("SR-NEW-43", rows.getString(1));
                assertEquals("ANANYA-SR-2026-001", rows.getString(2));
                rows.next();
                assertEquals("SR-LFT-15-44", rows.getString(1));
                assertNull(rows.getString(2));
            }
        }
    }
}
