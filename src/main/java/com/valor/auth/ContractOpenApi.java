package com.valor.auth;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.Content;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** Shared documentation only; does not intercept or change runtime responses. */
@Configuration
class ContractOpenApi {
    @Bean OpenApiCustomizer errorsAndNullData() {
        return api -> {
            Schema<?> nullableData = new ObjectSchema().nullable(true).description("No result data on errors; null or an empty object.");
            Schema<?> error = new ObjectSchema().addProperty("success", new BooleanSchema().example(false))
                .addProperty("message", new StringSchema()).addProperty("status", new IntegerSchema())
                .addProperty("timestamp", new StringSchema().format("date-time")).addProperty("data", nullableData);
            api.getComponents().addSchemas("ApiErrorResponse", error);
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1")) return;
                item.readOperations().forEach(op -> {
                    for (String code : new String[]{"400", "401", "403", "404", "409"}) {
                        op.getResponses().addApiResponse(code, new ApiResponse().description("Secret-free error envelope")
                            .content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))));
                    }
                });
            });
            var deactivation = api.getPaths().get("/api/v1/admin/users/{userId}");
            if (deactivation != null && deactivation.getDelete() != null && deactivation.getDelete().getResponses() != null) {
                var response = deactivation.getDelete().getResponses().get("200");
                if (response != null && response.getContent() != null) {
                    response.getContent().values().forEach(media -> media.setExample(java.util.Map.of(
                        "success", true,
                        "message", "Staff deactivated",
                        "status", 200,
                        "timestamp", "2030-01-01T00:00:00",
                        "data", java.util.Map.of(
                            "userId", 1,
                            "email", "technician@example.test",
                            "role", "TECHNICIAN",
                            "active", false,
                            "technicianProfileId", 1,
                            "employeeId", "TECH-001",
                            "assignedArea", "North",
                            "specialization", "Maintenance",
                            "availabilityStatus", "AVAILABLE"
                        )
                    )));
                }
            }
            Map<String, Schema> schemas = api.getComponents().getSchemas();
            if (schemas != null) {
                for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
                    Schema<?> schema = entry.getValue();
                    if (schema == null || schema.getProperties() == null) continue;
                    for (String field : new String[]{"activeAssignment", "report", "customerProfile", "technicianProfile"}) {
                        Schema<?> original = (Schema<?>) schema.getProperties().get(field);
                        if (original != null) {
                            schema.addProperty(field, new ComposedSchema().addAllOfItem(original).type("object").nullable(true));
                        }
                    }
                }
            }
            Schema<?> empty = api.getComponents().getSchemas().get("ApiResponseVoid");
            if (empty != null) {
                empty.addProperty("data", new Schema<>().nullable(true).description("Always null; the envelope message reports the operation result."));
            }
            if (schemas != null) {
                for (Schema<?> schema : schemas.values()) {
                    if (schema == null || schema.getProperties() == null || !schema.getProperties().containsKey("data")) continue;
                    Schema<?> data = schema.getProperties().get("data");
                    if (data != null) {
                        data.setNullable(true);
                        data.setDescription(data.getDescription() == null || data.getDescription().isBlank()
                            ? "Always null for operations without a payload; the envelope message reports the outcome."
                            : data.getDescription());
                    }
                }
            }
        };
    }
}
