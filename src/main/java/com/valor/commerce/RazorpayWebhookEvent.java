package com.valor.commerce;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "RazorpayWebhookEvent")
@Table(name = "razorpay_webhook_events")
@Getter @Setter
class RazorpayWebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true, length = 120)
    private String eventId;
    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;
    @Column(name = "razorpay_order_id", length = 80)
    private String razorpayOrderId;
    @Column(name = "razorpay_payment_id", length = 80)
    private String razorpayPaymentId;
    @Column(name = "razorpay_refund_id", length = 80)
    private String razorpayRefundId;
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();
}
