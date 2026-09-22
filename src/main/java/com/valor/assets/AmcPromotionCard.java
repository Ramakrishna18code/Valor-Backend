package com.valor.assets;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "amc_promotion_cards")
public class AmcPromotionCard {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
  @Column(name = "slot_number", nullable = false, unique = true) Integer slotNumber;
  @Column(nullable = false, length = 80) String quote;
  @Column(nullable = false, length = 120) String title;
  @Column(name = "supporting_text", nullable = false, length = 240) String supportingText;
  @Column(name = "cta_label", nullable = false, length = 60) String ctaLabel;
  @Column(name = "image_url", length = 500) String imageUrl;
  @Column(name = "background_color", nullable = false, length = 20) String backgroundColor = "#EAF5FF";
  @Column(nullable = false) boolean active = true;
  @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt;
  @PrePersist void p() { updatedAt = LocalDateTime.now(); }
  @PreUpdate void u() { updatedAt = LocalDateTime.now(); }
}
