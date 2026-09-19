package com.valor.pricing;

import com.valor.assets.Lift;
import com.valor.assets.LiftDoorType;
import com.valor.workflow.ServiceRequest;
import com.valor.workflow.WorkflowServiceType;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
    public static final int BASE_FLOORS = 5;
    public static final BigDecimal EXTRA_FLOOR_CHARGE = BigDecimal.valueOf(200);
    public static final BigDecimal MANUAL_SERVICE_BASE = BigDecimal.valueOf(999);
    public static final BigDecimal AUTO_SERVICE_BASE = BigDecimal.valueOf(1499);
    public static final BigDecimal MANUAL_AMC_BASE = BigDecimal.valueOf(14999);
    public static final BigDecimal AUTO_AMC_BASE = BigDecimal.valueOf(24999);

    public BigDecimal servicePrice(ServiceRequest request) {
        if (request == null) throw new PricingException("Service request is required for pricing");
        return servicePrice(request.getServiceType(), request.getLift());
    }

    public BigDecimal servicePrice(WorkflowServiceType serviceType, Lift lift) {
        if (serviceType != WorkflowServiceType.ROUTINE_MAINTENANCE && serviceType != WorkflowServiceType.BREAKDOWN) {
            throw new PricingException("Pricing is configured only for General Service and Breakdown Visit");
        }
        return base(lift, MANUAL_SERVICE_BASE, AUTO_SERVICE_BASE).add(extraFloorCharge(lift));
    }

    public BigDecimal amcPrice(Lift lift) {
        return base(lift, MANUAL_AMC_BASE, AUTO_AMC_BASE).add(extraFloorCharge(lift));
    }

    public BigDecimal extraFloorCharge(Lift lift) {
        int floors = floorCount(lift);
        return EXTRA_FLOOR_CHARGE.multiply(BigDecimal.valueOf(Math.max(0, floors - BASE_FLOORS)));
    }

    private BigDecimal base(Lift lift, BigDecimal manual, BigDecimal auto) {
        LiftDoorType doorType = doorType(lift);
        return doorType == LiftDoorType.MANUAL ? manual : auto;
    }

    private LiftDoorType doorType(Lift lift) {
        if (lift == null) throw new PricingException("Lift is required for pricing");
        if (lift.getDoorType() == null) throw new PricingException("Lift door type is required for pricing");
        return lift.getDoorType();
    }

    private int floorCount(Lift lift) {
        if (lift == null) throw new PricingException("Lift is required for pricing");
        if (lift.getFloorCount() == null) throw new PricingException("Lift floor count is required for pricing");
        if (lift.getFloorCount() < 0) throw new PricingException("Lift floor count is invalid");
        return lift.getFloorCount();
    }
}
