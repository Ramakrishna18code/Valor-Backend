package com.valor.pricing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valor.auth.AuditService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class EstimateCatalogueService {
    public record Item(String id, String category, String name, BigDecimal min, BigDecimal max, String note, boolean active) {}
    public record Catalogue(long version, int gstPercent, boolean testOnly, List<Item> items) {}
    public record Write(long version, List<Item> items) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    public EstimateCatalogueService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc; this.mapper = mapper; this.audit = audit;
    }
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Catalogue draft() { return read(false); }
    @PreAuthorize("isAuthenticated()")
    public Catalogue published() {
        Catalogue value = read(true);
        return new Catalogue(value.version(), 18, true, value.items().stream().filter(Item::active).toList());
    }
    private Catalogue read(boolean published) {
        return jdbc.queryForObject("select version, published_version, draft_json, published_json from service_estimate_catalogue where id=1", (rs, row) -> {
            try { return new Catalogue(rs.getLong(published ? "published_version" : "version"), 18, true,
                mapper.readValue(rs.getString(published ? "published_json" : "draft_json"), new TypeReference<List<Item>>() {}));
            } catch (Exception e) { throw new IllegalStateException("Unable to read estimate catalogue", e); }
        });
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Catalogue save(Write input) {
        validate(input.items());
        String json;
        try { json = mapper.writeValueAsString(input.items()); }
        catch (Exception e) { throw new IllegalStateException(e); }
        int updated = jdbc.update("update service_estimate_catalogue set draft_json=?, version=version+1 where id=1 and version=?", json, input.version());
        if (updated != 1) throw conflict();
        audit.record("ESTIMATE_DRAFT_SAVED", "SERVICE_ESTIMATES", 1, "Saved test catalogue with " + input.items().size() + " entries; revision " + (input.version()+1));
        return read(false);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Catalogue publish(long version) {
        int updated = jdbc.update("update service_estimate_catalogue set published_json=draft_json, published_version=version+1, version=version+1 where id=1 and version=?", version);
        if (updated != 1) throw conflict();
        audit.record("ESTIMATES_PUBLISHED", "SERVICE_ESTIMATES", 1, "Published test estimates; revision " + (version+1));
        return read(false);
    }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "Prices changed in another session. Reload before editing."); }
    static void validate(List<Item> items) {
        if (items == null || items.size() > 500) throw invalid("Catalogue must contain at most 500 services");
        Set<String> ids = new HashSet<>();
        for (Item item : items) {
            if (item == null || !text(item.id(), 80) || !ids.add(item.id()) || !text(item.category(), 100) || !text(item.name(), 200)) throw invalid("Every service needs a unique ID, category and name");
            if (item.note() != null && item.note().length() > 1000) throw invalid("Service note is too long");
            for (BigDecimal amount : Arrays.asList(item.min(), item.max())) {
                if (amount != null && (amount.signum() < 0 || amount.compareTo(new BigDecimal("100000000")) > 0 || amount.stripTrailingZeros().scale() > 2)) throw invalid("Prices must be between 0 and 100000000 with at most two decimals");
            }
            if (item.max() != null && (item.min() == null || item.max().compareTo(item.min()) < 0)) throw invalid("Maximum estimate must be at least the minimum estimate");
        }
    }
    private static boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
