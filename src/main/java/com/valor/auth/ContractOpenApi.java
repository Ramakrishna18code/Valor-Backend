package com.valor.auth;

import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.Content;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared documentation only; does not intercept or change runtime responses. */
@Configuration
class ContractOpenApi {
    @Bean OpenApiCustomizer errorsAndNullData() {
        return api -> {
            Schema<?> nullableData=new ObjectSchema().nullable(true).description("No result data on errors; null or an empty object.");
            Schema<?> error=new ObjectSchema().addProperty("success",new BooleanSchema().example(false))
                .addProperty("message",new StringSchema()).addProperty("status",new IntegerSchema())
                .addProperty("timestamp",new StringSchema().format("date-time")).addProperty("data",nullableData);
            api.getComponents().addSchemas("ApiErrorResponse",error);
            api.getPaths().forEach((path,item)->{
                if(!path.startsWith("/api/v1"))return;
                item.readOperations().forEach(op->{
                    for(String code:new String[]{"400","401","403","404","409"})
                        op.getResponses().addApiResponse(code,new ApiResponse().description("Secret-free error envelope")
                            .content(new Content().addMediaType("application/json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))));
                });
            });
            var deactivation=api.getPaths().get("/api/v1/admin/users/{userId}").getDelete();
            deactivation.getResponses().get("200").getContent().values().forEach(media->media.setExample(java.util.Map.of(
                "success",true,"message","Staff deactivated","status",200,"timestamp","2030-01-01T00:00:00",
                "data",java.util.Map.of("userId",1,"email","technician@example.test","role","TECHNICIAN","active",false,
                    "technicianProfileId",1,"employeeId","TECH-001","assignedArea","North","specialization","Maintenance","availabilityStatus","AVAILABLE"))));
            api.getComponents().getSchemas().values().forEach(schema->{
                if(schema.getProperties()==null)return;
                for(String field:new String[]{"activeAssignment","report","customerProfile","technicianProfile"}) {
                    Schema<?> original=(Schema<?>)schema.getProperties().get(field);
                    if(original!=null)schema.addProperty(field,new ComposedSchema().addAllOfItem(original).type("object").nullable(true));
                }
            });
            Schema<?> empty=api.getComponents().getSchemas().get("ApiResponseVoid");
            if(empty!=null)empty.addProperty("data",new Schema<>().nullable(true).description("Always null; the envelope message reports the operation result."));
        };
    }
}
