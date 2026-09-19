package com.valor.auth;

import com.valor.assets.Lift;
import com.valor.assets.LiftDoorType;
import com.valor.pricing.PricingException;
import com.valor.pricing.PricingService;
import com.valor.workflow.ServiceRequest;
import com.valor.workflow.WorkflowServiceType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientPricingServiceTest {
    private final PricingService pricing = new PricingService();

    @Test void servicePricingUsesG5BaseAndExtraFloors() {
        assertEquals(BigDecimal.valueOf(999), pricing.servicePrice(WorkflowServiceType.ROUTINE_MAINTENANCE, lift(LiftDoorType.MANUAL, 5)));
        assertEquals(BigDecimal.valueOf(1499), pricing.servicePrice(WorkflowServiceType.BREAKDOWN, lift(LiftDoorType.AUTO, 5)));
        assertEquals(BigDecimal.valueOf(1199), pricing.servicePrice(WorkflowServiceType.ROUTINE_MAINTENANCE, lift(LiftDoorType.MANUAL, 6)));
        assertEquals(BigDecimal.valueOf(1699), pricing.servicePrice(WorkflowServiceType.BREAKDOWN, lift(LiftDoorType.AUTO, 6)));
        assertEquals(BigDecimal.valueOf(1599), pricing.servicePrice(WorkflowServiceType.BREAKDOWN, lift(LiftDoorType.MANUAL, 8)));
    }

    @Test void amcPricingUsesDoorTypeAndExtraFloors() {
        assertEquals(BigDecimal.valueOf(14999), pricing.amcPrice(lift(LiftDoorType.MANUAL, 5)));
        assertEquals(BigDecimal.valueOf(24999), pricing.amcPrice(lift(LiftDoorType.AUTO, 5)));
        assertEquals(BigDecimal.valueOf(15199), pricing.amcPrice(lift(LiftDoorType.MANUAL, 6)));
        assertEquals(BigDecimal.valueOf(25199), pricing.amcPrice(lift(LiftDoorType.AUTO, 6)));
    }

    @Test void serviceRequestPricingIsBackendAuthoritativeAndRejectsUnsupportedInputs() {
        ServiceRequest request = new ServiceRequest();
        request.setServiceType(WorkflowServiceType.BREAKDOWN);
        request.setLift(lift(LiftDoorType.AUTO, 7));
        assertEquals(BigDecimal.valueOf(1899), pricing.servicePrice(request));
        assertThrows(PricingException.class, () -> pricing.servicePrice(WorkflowServiceType.EMERGENCY, lift(LiftDoorType.AUTO, 5)));
        assertThrows(PricingException.class, () -> pricing.amcPrice(lift(null, 5)));
        assertThrows(PricingException.class, () -> pricing.amcPrice(lift(LiftDoorType.MANUAL, null)));
    }

    private static Lift lift(LiftDoorType doorType, Integer floors) {
        Lift lift = new Lift();
        lift.setDoorType(doorType);
        lift.setFloorCount(floors);
        return lift;
    }
}
