package com.valor.parts;

import com.valor.auth.AssetIdentityAccess;
import com.valor.auth.Role;
import com.valor.auth.User;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "email.enabled=false",
        "smtp.enabled=false",
        "spring.task.scheduling.enabled=false",
        "logging.level.root=WARN"
})
@ActiveProfiles("prod")
class PartsMysqlSpringLifecycleTest {
    @Autowired PartsService service;
    @Autowired JdbcTemplate jdbc;
    @MockBean AssetIdentityAccess identities;
    @SpyBean PartItemRepository itemRepository;

    @RepeatedTest(3)
    void competingApprovalsReserveLimitedStockWithoutNegativeOrPartialState() throws Exception {
        String tag = "db29-" + UUID.randomUUID();
        Fixture fixture = fixture(tag, 1);
        User admin = admin();
        when(identities.actor()).thenReturn(admin);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<Boolean> first = pool.submit(() -> approve(fixture.firstRequestId(), ready));
            Future<Boolean> second = pool.submit(() -> approve(fixture.secondRequestId(), ready));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers reached the bounded start barrier");
            boolean firstResult = first.get(15, TimeUnit.SECONDS);
            boolean secondResult = second.get(15, TimeUnit.SECONDS);

            int successes = (firstResult ? 1 : 0) + (secondResult ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(0, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, fixture.itemId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from part_stock_movements where part_item_id=? and movement_type='RESERVE'", Integer.class, fixture.itemId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id in (?,?) and status='RESERVED'", Integer.class, fixture.firstRequestId(), fixture.secondRequestId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id in (?,?) and status='SUBMITTED'", Integer.class, fixture.firstRequestId(), fixture.secondRequestId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationRestoresReservedStockOnceAndRejectsRepeat() {
        String tag = "db29-cancel-" + UUID.randomUUID();
        Fixture fixture = fixture(tag, 2);
        User admin = admin();
        when(identities.actor()).thenReturn(admin);
        assertDoesNotThrow(() -> service.approve(fixture.firstRequestId(), true, null));
        User technician = technicianUser(fixture.technicianUserId());
        when(identities.actor()).thenReturn(technician);

        assertDoesNotThrow(() -> service.cancel(fixture.firstRequestId()));
        assertThrows(RuntimeException.class, () -> service.cancel(fixture.firstRequestId()));
        assertEquals(2, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, fixture.itemId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from part_stock_movements where part_item_id=? and movement_type='RELEASE'", Integer.class, fixture.itemId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id=? and status='CANCELLED'", Integer.class, fixture.firstRequestId()));
    }

    @RepeatedTest(3)
    void concurrentCancellationRestoresReservedStockOnlyOnce() throws Exception {
        String tag = "db29-concurrent-cancel-" + UUID.randomUUID();
        Fixture fixture = fixture(tag, 2);
        User admin = admin();
        when(identities.actor()).thenReturn(admin);
        service.approve(fixture.firstRequestId(), true, null);
        User technician = technicianUser(fixture.technicianUserId());
        when(identities.actor()).thenReturn(technician);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<Boolean> first = pool.submit(() -> cancel(fixture.firstRequestId(), ready));
            Future<Boolean> second = pool.submit(() -> cancel(fixture.firstRequestId(), ready));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(2, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, fixture.itemId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from part_stock_movements where part_item_id=? and movement_type='RELEASE'", Integer.class, fixture.itemId()));
            assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id=? and status='CANCELLED'", Integer.class, fixture.firstRequestId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(2)
    void multiItemApprovalRollsBackAfterSecondItemFailure() {
        String tag = "db30-rollback-" + UUID.randomUUID();
        Fixture fixture = fixture(tag, 2);
        long secondItem = createSecondItemAndLine(tag, fixture);
        User admin = admin();
        when(identities.actor()).thenReturn(admin);

        AtomicInteger saves = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (fail.get() && saves.incrementAndGet() == 2) throw new IllegalStateException("synthetic second-item failure");
            return null;
        }).when(itemRepository).save(any(PartItem.class));

        assertThrows(IllegalStateException.class, () -> service.approve(fixture.firstRequestId(), true, null));
        assertEquals(2, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, fixture.itemId()));
        assertEquals(2, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, secondItem));
        assertEquals(0, jdbc.queryForObject("select count(*) from part_stock_movements where service_request_id=? and movement_type='RESERVE'", Integer.class, fixture.firstRequestId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from part_request_events where request_id=?", Integer.class, fixture.firstRequestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id=? and status='SUBMITTED'", Integer.class, fixture.firstRequestId()));
        assertEquals(2, jdbc.queryForObject("select count(*) from service_request_part_lines where request_id=? and approved_quantity=0", Integer.class, fixture.firstRequestId()));

        fail.set(false);
        saves.set(0);
        assertDoesNotThrow(() -> service.approve(fixture.firstRequestId(), true, null));
        assertEquals(1, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, fixture.itemId()));
        assertEquals(1, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, secondItem));
        assertEquals(2, jdbc.queryForObject("select count(*) from part_stock_movements where service_request_id=? and movement_type='RESERVE'", Integer.class, fixture.firstRequestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id=? and status='RESERVED'", Integer.class, fixture.firstRequestId()));
    }

    @RepeatedTest(3)
    void approvalAndCancellationRaceLeavesOneValidTerminalOutcome() throws Exception {
        String tag = "db30-approve-cancel-" + UUID.randomUUID();
        Fixture fixture = fixture(tag, 1);
        User admin = admin();
        User technician = technicianUser(fixture.technicianUserId());
        when(identities.actor()).thenAnswer(invocation -> Thread.currentThread().getName().contains("cancel") ? technician : admin);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<Boolean> approval = pool.submit(() -> { Thread.currentThread().setName("db30-approval"); return approve(fixture.firstRequestId(), ready); });
            Future<Boolean> cancellation = pool.submit(() -> { Thread.currentThread().setName("db30-cancel"); return cancel(fixture.firstRequestId(), ready); });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            approval.get(15, TimeUnit.SECONDS);
            cancellation.get(15, TimeUnit.SECONDS);
            String status = jdbc.queryForObject("select status from service_request_part_requests where id=?", String.class, fixture.firstRequestId());
            int stock = jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, fixture.itemId());
            int reserves = jdbc.queryForObject("select count(*) from part_stock_movements where service_request_id=? and movement_type='RESERVE'", Integer.class, fixture.firstRequestId());
            int releases = jdbc.queryForObject("select count(*) from part_stock_movements where service_request_id=? and movement_type='RELEASE'", Integer.class, fixture.firstRequestId());
            assertTrue(status.equals("RESERVED") || status.equals("CANCELLED"));
            if (status.equals("RESERVED")) { assertEquals(0, stock); assertEquals(1, reserves); assertEquals(0, releases); }
            else {
                assertEquals(1, stock);
                assertTrue((reserves == 0 && releases == 0) || (reserves == 1 && releases == 1));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(3)
    void oppositeInputOrderApprovalsLockItemsInIdOrder() throws Exception {
        String tag = "db33-opposite-" + UUID.randomUUID();
        Fixture base = fixture(tag, 1);
        long secondItem = createSecondItem(tag, 1);
        long technicianProfile = jdbc.queryForObject("select id from technician_profiles where user_id=?", Long.class, base.technicianUserId());
        long serviceRequestA = jdbc.queryForObject("select service_request_id from service_request_part_requests where id=?", Long.class, base.firstRequestId());
        long serviceRequestB = jdbc.queryForObject("select service_request_id from service_request_part_requests where id=?", Long.class, base.secondRequestId());
        long first = partRequest(tag + " opposite first", serviceRequestA, technicianProfile);
        long second = partRequest(tag + " opposite second", serviceRequestB, technicianProfile);
        line(first, base.itemId(), 1); line(first, secondItem, 1);
        line(second, secondItem, 1); line(second, base.itemId(), 1);
        assertEquals(List.of(base.itemId(), secondItem), jdbc.query("select part_item_id from service_request_part_lines where request_id=? order by id", (rs, n) -> rs.getLong(1), first));
        assertEquals(List.of(secondItem, base.itemId()), jdbc.query("select part_item_id from service_request_part_lines where request_id=? order by id", (rs, n) -> rs.getLong(1), second));
        User admin = admin();
        when(identities.actor()).thenReturn(admin);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<Boolean> a = pool.submit(() -> approve(first, ready));
            Future<Boolean> b = pool.submit(() -> approve(second, ready));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            int successes = (a.get(15, TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(0, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, base.itemId()));
            assertEquals(0, jdbc.queryForObject("select quantity_on_hand from part_items where id=?", Integer.class, secondItem));
            assertEquals(2, jdbc.queryForObject("select count(*) from part_stock_movements where movement_type='RESERVE' and part_item_id in (?,?) and service_request_id in (?,?)", Integer.class, base.itemId(), secondItem, serviceRequestA, serviceRequestB));
            assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id in (?,?) and status='RESERVED'", Integer.class, first, second));
            assertEquals(1, jdbc.queryForObject("select count(*) from service_request_part_requests where id in (?,?) and status='SUBMITTED'", Integer.class, first, second));
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean approve(Long id, CountDownLatch ready) {
        ready.countDown();
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("approval barrier timed out");
            service.approve(id, true, null);
            return true;
        } catch (RuntimeException expectedForOneLoser) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private boolean cancel(Long id, CountDownLatch ready) {
        ready.countDown();
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("cancellation barrier timed out");
            service.cancel(id);
            return true;
        } catch (RuntimeException expectedForOneLoser) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private Fixture fixture(String tag, int stock) {
        long now = System.currentTimeMillis() * 1000;
        jdbc.update("insert into users(email,password_hash,role,is_active,is_locked,created_at,updated_at) values(?,?,?,true,false,current_timestamp(6),current_timestamp(6))", tag + "-admin@example.test", "hash", "ADMIN");
        long admin = jdbc.queryForObject("select id from users where email=?", Long.class, tag + "-admin@example.test");
        jdbc.update("insert into users(email,password_hash,role,is_active,is_locked,created_at,updated_at) values(?,?,?,true,false,current_timestamp(6),current_timestamp(6))", tag + "-tech@example.test", "hash", "TECHNICIAN");
        long techUser = jdbc.queryForObject("select id from users where email=?", Long.class, tag + "-tech@example.test");
        String employeeId = "e" + tag.substring(tag.length() - 10);
        jdbc.update("insert into technician_profiles(user_id,employee_id,availability_status,is_active,created_at,updated_at) values(?,?, 'AVAILABLE',true,current_timestamp(6),current_timestamp(6))", techUser, employeeId);
        long tech = jdbc.queryForObject("select id from technician_profiles where employee_id=?", Long.class, employeeId);
        jdbc.update("insert into users(email,password_hash,role,is_active,is_locked,created_at,updated_at) values(?,?,?,true,false,current_timestamp(6),current_timestamp(6))", tag + "-customer@example.test", "hash", "CUSTOMER");
        long customerUser = jdbc.queryForObject("select id from users where email=?", Long.class, tag + "-customer@example.test");
        jdbc.update("insert into customer_profiles(user_id,full_name,status,is_active,created_at,updated_at) values(?,?, 'ACTIVE',true,current_timestamp(6),current_timestamp(6))", customerUser, tag + " customer");
        long customer = jdbc.queryForObject("select id from customer_profiles where user_id=?", Long.class, customerUser);
        jdbc.update("insert into buildings(customer_id,building_name,status,is_active,created_at,updated_at) values(?,?, 'ACTIVE',true,current_timestamp(6),current_timestamp(6))", customer, tag + " building");
        long building = jdbc.queryForObject("select id from buildings where building_name=?", Long.class, tag + " building");
        jdbc.update("insert into lifts(building_id,name,current_status,is_active,created_at,updated_at) values(?,?, 'ACTIVE',true,current_timestamp(6),current_timestamp(6))", building, tag + " lift");
        long lift = jdbc.queryForObject("select id from lifts where name=?", Long.class, tag + " lift");
        jdbc.update("insert into part_categories(name,is_active,created_at,updated_at) values(?,true,current_timestamp(6),current_timestamp(6))", tag + " category");
        long category = jdbc.queryForObject("select id from part_categories where name=?", Long.class, tag + " category");
        jdbc.update("insert into part_items(category_id,name,sku,unit,quantity_on_hand,reorder_threshold,is_active,version,created_at,updated_at) values(?,?,?,?,?,?,true,0,current_timestamp(6),current_timestamp(6))", category, tag + " item", tag + " sku", "EA", stock, 0);
        long item = jdbc.queryForObject("select id from part_items where sku=?", Long.class, tag + " sku");
        long requestA = request(tag + "-a", customer, lift, admin, tech);
        long requestB = request(tag + "-b", customer, lift, admin, tech);
        long partA = partRequest(tag + " part a", requestA, tech);
        long partB = partRequest(tag + " part b", requestB, tech);
        line(partA, item, 1); line(partB, item, 1);
        return new Fixture(item, partA, partB, techUser);
    }

    private long request(String serviceId, long customer, long lift, long admin, long tech) {
        jdbc.update("insert into service_requests(customer_id,lift_id,service_id,title,description,priority,status,service_type,service_requested_at,created_at,updated_at) values(?,?,?,?,?,'MEDIUM','WAITING_FOR_PARTS','BREAKDOWN',current_timestamp(6),current_timestamp(6),current_timestamp(6))", customer, lift, serviceId, serviceId + " title", "synthetic request");
        long id = jdbc.queryForObject("select id from service_requests where service_id=?", Long.class, serviceId);
        jdbc.update("insert into technician_assignments(service_request_id,technician_id,assigned_by_user_id,status,assigned_at,created_at,updated_at) values(?,?,?,'ACCEPTED',current_timestamp(6),current_timestamp(6),current_timestamp(6))", id, tech, admin);
        return id;
    }

    private long partRequest(String note, long serviceRequest, long tech) {
        jdbc.update("insert into service_request_part_requests(service_request_id,technician_profile_id,status,notes,created_at,submitted_at) values(?,?, 'SUBMITTED',?,current_timestamp(6),current_timestamp(6))", serviceRequest, tech, note);
        return jdbc.queryForObject("select id from service_request_part_requests where notes=?", Long.class, note);
    }

    private void line(long request, long item, int quantity) {
        jdbc.update("insert into service_request_part_lines(request_id,part_item_id,requested_quantity,approved_quantity,issued_quantity) values(?,?,?,0,0)", request, item, quantity);
    }

    private long createSecondItemAndLine(String tag, Fixture fixture) {
        jdbc.update("insert into part_categories(name,is_active,created_at,updated_at) values(?,true,current_timestamp(6),current_timestamp(6))", tag + " second category");
        long category = jdbc.queryForObject("select id from part_categories where name=?", Long.class, tag + " second category");
        jdbc.update("insert into part_items(category_id,name,sku,unit,quantity_on_hand,reorder_threshold,is_active,version,created_at,updated_at) values(?,?,?,?,?,?,true,0,current_timestamp(6),current_timestamp(6))", category, tag + " second item", tag + " second sku", "EA", 2, 0);
        long item = jdbc.queryForObject("select id from part_items where sku=?", Long.class, tag + " second sku");
        line(fixture.firstRequestId(), item, 1);
        return item;
    }

    private long createSecondItem(String tag, int stock) {
        jdbc.update("insert into part_categories(name,is_active,created_at,updated_at) values(?,true,current_timestamp(6),current_timestamp(6))", tag + " second category");
        long category = jdbc.queryForObject("select id from part_categories where name=?", Long.class, tag + " second category");
        jdbc.update("insert into part_items(category_id,name,sku,unit,quantity_on_hand,reorder_threshold,is_active,version,created_at,updated_at) values(?,?,?,?,?,?,true,0,current_timestamp(6),current_timestamp(6))", category, tag + " second item", tag + " second sku", "EA", stock, 0);
        return jdbc.queryForObject("select id from part_items where sku=?", Long.class, tag + " second sku");
    }

    private User admin() { User user = mock(User.class); when(user.getId()).thenReturn(1L); when(user.getRole()).thenReturn(Role.ADMIN); return user; }
    private User technicianUser(long id) { User user = mock(User.class); when(user.getId()).thenReturn(id); when(user.getRole()).thenReturn(Role.TECHNICIAN); return user; }
    private record Fixture(long itemId, long firstRequestId, long secondRequestId, long technicianUserId) {}
}
