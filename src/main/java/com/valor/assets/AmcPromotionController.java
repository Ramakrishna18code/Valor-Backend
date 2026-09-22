package com.valor.assets;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

interface AmcPromotionRepository extends JpaRepository<AmcPromotionCard, Long> {
  List<AmcPromotionCard> findByActiveTrueOrderBySlotNumberAsc();
  List<AmcPromotionCard> findAllByOrderBySlotNumberAsc();
}

@RestController
@RequestMapping("/api/v1")
class AmcPromotionController {
  private final AmcPromotionRepository cards;
  AmcPromotionController(AmcPromotionRepository cards) { this.cards = cards; }

  @GetMapping("/customers/me/amc-promotions")
  ApiResponse<List<View>> customerCards() { return ok(cards.findByActiveTrueOrderBySlotNumberAsc().stream().map(this::view).toList()); }

  @GetMapping("/admin/amc-promotions")
  ApiResponse<List<View>> adminCards() { return ok(cards.findAllByOrderBySlotNumberAsc().stream().map(this::view).toList()); }

  @PutMapping("/admin/amc-promotions/{id}")
  ApiResponse<View> update(@PathVariable Long id, @Valid @RequestBody Update input) {
    AmcPromotionCard card = cards.findById(id).orElseThrow(() -> new IllegalArgumentException("Promotion card not found"));
    card.quote = input.quote().trim();
    card.title = input.title().trim();
    card.supportingText = input.supportingText().trim();
    card.ctaLabel = input.ctaLabel().trim();
    card.imageUrl = clean(input.imageUrl());
    card.backgroundColor = clean(input.backgroundColor()) == null ? "#EAF5FF" : input.backgroundColor().trim();
    card.active = input.active();
    return ok(view(cards.save(card)));
  }

  private View view(AmcPromotionCard c) {
    return new View(c.id, c.slotNumber, c.quote, c.title, c.supportingText, c.ctaLabel, c.imageUrl, c.backgroundColor, c.active);
  }
  private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

  interface StrictInput { @JsonAnySetter default void rejectUnknown(String key, JsonNode value) { throw new IllegalArgumentException("Unsupported field"); } }
  record Update(@NotBlank @Size(max = 80) String quote, @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 240) String supportingText, @NotBlank @Size(max = 60) String ctaLabel,
      @Size(max = 500) String imageUrl, @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String backgroundColor,
      boolean active) implements StrictInput {}
  record View(Long id, Integer slotNumber, String quote, String title, String supportingText, String ctaLabel,
      String imageUrl, String backgroundColor, boolean active) {}
  private static <T> ApiResponse<T> ok(T data) { return ApiResponse.success("OK", data, 200); }
}
