package com.valor.commerce;

import com.valor.notifications.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Only committed payout events are delivered; the notification event key makes retries safe. */
@Component
class FinanceNotificationOutbox {
    private final JdbcTemplate db;private final NotificationService notifications;
    FinanceNotificationOutbox(JdbcTemplate db,NotificationService notifications){this.db=db;this.notifications=notifications;}
    @Scheduled(fixedDelayString="${app.finance.notification-delay-ms:30000}",initialDelayString="${app.finance.notification-initial-delay-ms:60000}")
    @Transactional
    public void deliver(){
        var rows=db.queryForList("SELECT id,payout_batch_id,user_id,message FROM finance_notification_outbox WHERE status='PENDING' ORDER BY id LIMIT 25 FOR UPDATE");
        for(var row:rows){try{notifications.createSystemOnce(((Number)row.get("user_id")).longValue(),"FINANCE_PAYOUT_"+row.get("payout_batch_id")+"_"+row.get("user_id"),"Payout confirmed",(String)row.get("message"));db.update("UPDATE finance_notification_outbox SET status='SENT',sent_at=CURRENT_TIMESTAMP,attempts=attempts+1 WHERE id=?",row.get("id"));}
            catch(RuntimeException failure){db.update("UPDATE finance_notification_outbox SET attempts=attempts+1 WHERE id=?",row.get("id"));}}
    }
}
