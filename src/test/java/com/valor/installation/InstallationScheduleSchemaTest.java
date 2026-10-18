package com.valor.installation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:installation_schedule_schema;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
class InstallationScheduleSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void v51IsAppliedWithVersionAndApprovalUniqueness() {
        assertEquals(1, count("select count(*) from information_schema.tables where table_name='INSTALLATION_SCHEDULES'"));
        assertEquals(1, count("select count(*) from information_schema.tables where table_name='INSTALLATION_SCHEDULE_APPROVALS'"));
        assertEquals(1, count("select count(*) from information_schema.table_constraints where table_name='INSTALLATION_SCHEDULES' and constraint_name='UK_INSTALLATION_SCHEDULE_VERSION'"));
        assertEquals(1, count("select count(*) from information_schema.table_constraints where table_name='INSTALLATION_SCHEDULE_APPROVALS' and constraint_name='UK_INSTALLATION_SCHEDULE_APPROVAL_ONCE'"));
        assertEquals(1, count("select count(*) from information_schema.check_constraints where constraint_name='CK_INSTALLATION_SCHEDULE_STATUS'"));
        assertEquals(1, count("select count(*) from information_schema.check_constraints where constraint_name='CK_INSTALLATION_SCHEDULE_APPROVAL_TYPE'"));
    }

    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
}
