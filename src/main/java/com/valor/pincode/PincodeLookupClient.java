package com.valor.pincode;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

interface PincodeLookupClient {
    List<PincodeDtos.Location> lookup(String pincode);
}

@Service
final class ExternalPincodeLookupClient implements PincodeLookupClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalPincodeLookupClient.class);
    private final RestClient client;
    private final String baseUrl;

    ExternalPincodeLookupClient(
            @Value("${app.pincode.lookup-url:https://api.postalpincode.in}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "ValorBackend/1.0 (PIN lookup)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public List<PincodeDtos.Location> lookup(String pincode) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/pincode/{pincode}")
                .buildAndExpand(pincode)
                .encode()
                .toUri();
        try {
            JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
            return parse(body, pincode);
        } catch (PincodeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("External PIN lookup failed for provider request {}: {}", uri, e.getClass().getSimpleName());
            throw new PincodeException(502, "PIN lookup is temporarily unavailable. Enter the address manually.");
        }
    }

    private static List<PincodeDtos.Location> parse(JsonNode body, String requestedPin) {
        if (body == null || !body.isArray() || body.isEmpty()) {
            throw new PincodeException(502, "PIN lookup returned an invalid response. Enter the address manually.");
        }
        JsonNode envelope = body.get(0);
        if (envelope == null || !"Success".equalsIgnoreCase(envelope.path("Status").asText())) {
            throw new PincodeException(404, "This PIN code was not found. Enter the address manually.");
        }
        JsonNode offices = envelope.get("PostOffice");
        if (offices == null || !offices.isArray()) {
            throw new PincodeException(404, "This PIN code was not found. Enter the address manually.");
        }
        List<PincodeDtos.Location> result = new ArrayList<>();
        for (JsonNode office : offices) {
            String name = text(office, "Name");
            String pin = text(office, "Pincode");
            String state = text(office, "State");
            String district = text(office, "District");
            if (name.isBlank() || !pin.equals(requestedPin) || state.isBlank() || district.isBlank()) continue;
            result.add(new PincodeDtos.Location(pin, state, district, name, textOrNull(office, "Name"),
                    "India Post API", textOrNull(office, "Division"), null));
        }
        if (result.isEmpty()) throw new PincodeException(404, "This PIN code was not found. Enter the address manually.");
        result.sort(java.util.Comparator.comparing(PincodeDtos.Location::postOffice));
        return result;
    }

    private static String text(JsonNode node, String field) { return node.path(field).asText("").trim(); }
    private static String textOrNull(JsonNode node, String field) { String value = text(node, field); return value.isBlank() ? null : value; }
}
