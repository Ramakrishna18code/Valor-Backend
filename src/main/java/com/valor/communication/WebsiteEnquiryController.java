package com.valor.communication;

import com.valor.auth.AssetIdentityAccess;
import com.valor.exception.ResourceNotFoundException;
import com.valor.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class WebsiteEnquiryController {
    private final JdbcTemplate db;
    private final AssetIdentityAccess identities;

    WebsiteEnquiryController(JdbcTemplate db, AssetIdentityAccess identities) {
        this.db = db;
        this.identities = identities;
    }

    record EnquiryInput(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "(?=(?:\\D*\\d){7,})[+0-9()\\s-]{7,24}") String phone,
            @NotBlank @Pattern(regexp = "SERVICE|CONTACT|EMERGENCY") String source,
            @Size(max = 160) String building,
            @Size(max = 80) String serviceType,
            @Size(max = 2000) String message) {}
    record Receipt(String reference) {}
    record Enquiry(long id, String reference, String source, String name, String phone,
                   String building, String serviceType, String message, String status,
                   LocalDateTime createdAt) {}
    record Page(List<Enquiry> items, int page, int size, long totalElements, long totalPages) {}
    record StatusInput(@Pattern(regexp = "CONTACTED|CLOSED") String status) {}

    @PostMapping("/public/website-enquiries")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Receipt> create(@Valid @RequestBody EnquiryInput input) {
        String reference = UUID.randomUUID().toString();
        db.update("INSERT INTO website_enquiries(reference_code,source,name,phone,building,service_type,message) VALUES(?,?,?,?,?,?,?)",
                reference, input.source(), input.name().trim(), input.phone().trim(), clean(input.building()),
                clean(input.serviceType()), clean(input.message()));
        return ApiResponse.success("Enquiry received", new Receipt(reference), 201);
    }

    @GetMapping("/admin/website-enquiries")
    ApiResponse<Page> list(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String status) {
        identities.requireAdmin();
        if (page < 0 || size < 1 || size > 100 || (status != null && !List.of("NEW", "CONTACTED", "CLOSED").contains(status))) {
            throw new IllegalArgumentException("Invalid enquiry filter");
        }
        String filter = status == null ? "" : " WHERE status=?";
        long total = status == null ? db.queryForObject("SELECT COUNT(*) FROM website_enquiries", Long.class)
                : db.queryForObject("SELECT COUNT(*) FROM website_enquiries WHERE status=?", Long.class, status);
        String sql = "SELECT id,reference_code,source,name,phone,building,service_type,message,status,created_at FROM website_enquiries"
                + filter + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?";
        Object[] args = status == null ? new Object[]{size, (long) page * size} : new Object[]{status, size, (long) page * size};
        var items = db.query(sql, (rs, row) -> new Enquiry(rs.getLong("id"), rs.getString("reference_code"),
                rs.getString("source"), rs.getString("name"), rs.getString("phone"), rs.getString("building"),
                rs.getString("service_type"), rs.getString("message"), rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()), args);
        return ApiResponse.success("Website enquiries", new Page(items, page, size, total, (total + size - 1) / size), 200);
    }

    @PatchMapping("/admin/website-enquiries/{id}/status")
    ApiResponse<Enquiry> update(@PathVariable long id, @Valid @RequestBody StatusInput input) {
        identities.requireAdmin();
        if (input.status() == null) throw new IllegalArgumentException("Status is required");
        if (db.update("UPDATE website_enquiries SET status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", input.status(), id) == 0) {
            throw new ResourceNotFoundException("Enquiry not found");
        }
        var item = db.queryForObject("SELECT id,reference_code,source,name,phone,building,service_type,message,status,created_at FROM website_enquiries WHERE id=?",
                (rs, row) -> new Enquiry(rs.getLong("id"), rs.getString("reference_code"), rs.getString("source"),
                        rs.getString("name"), rs.getString("phone"), rs.getString("building"), rs.getString("service_type"),
                        rs.getString("message"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()), id);
        return ApiResponse.success("Enquiry updated", item, 200);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
