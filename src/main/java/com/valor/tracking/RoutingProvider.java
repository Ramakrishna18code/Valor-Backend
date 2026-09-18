package com.valor.tracking;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

record RoutePoint(BigDecimal latitude, BigDecimal longitude) {}
record RouteResult(boolean available, Integer distanceMeters, Integer durationSeconds, String providerState, List<RoutePoint> polyline) {
    static RouteResult unavailable(String reason) { return new RouteResult(false, null, null, reason, List.of()); }
}

interface RoutingProvider {
    RouteResult route(RoutePoint origin, RoutePoint destination);
}

@Component
class UnavailableRoutingProvider implements RoutingProvider {
    @Override public RouteResult route(RoutePoint origin, RoutePoint destination) {
        return RouteResult.unavailable("PROVIDER_NOT_CONFIGURED");
    }
}
