package com.valor.locations;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
@Service
public class PlaceSearch {
 public record Place(String name,String address,double latitude,double longitude,String city,String state,String pincode) {}
 static class Failure extends RuntimeException { final int status; Failure(int status,String message){super(message);this.status=status;} }
 private record Cached(JsonNode value,Instant until) {}
 private final RestClient client; private final String base; private final Clock clock;
 private final Map<String,Cached> cache=new ConcurrentHashMap<>(); private Instant nextCall=Instant.MIN;
 public PlaceSearch(@Value("${app.maps.geocoder-url:https://nominatim.openstreetmap.org}") String base, @Value("${app.maps.user-agent:ValorElevators/1.0 (service location search)}") String userAgent, Clock clock) {
  this.base=base.replaceAll("/+$","");this.clock=clock;
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Duration.ofSeconds(4));factory.setReadTimeout(Duration.ofSeconds(8));
  client=RestClient.builder().requestFactory(factory).defaultHeader("User-Agent",userAgent).defaultHeader("Accept","application/json").build();
 }
 public List<Place> search(String query) {
  String q=query==null?"":query.trim();if(q.length()<3||q.length()>200)throw new Failure(400,"Enter a place or address with 3 to 200 characters");
  URI uri=UriComponentsBuilder.fromUriString(base).path("/search").queryParam("q",q).queryParam("format","jsonv2").queryParam("addressdetails",1).queryParam("limit",5).build().encode().toUri();
  JsonNode result=fetch(uri);var rows=new ArrayList<Place>();if(result.isArray())for(JsonNode row:result){Place place=place(row);if(place!=null)rows.add(place);}return rows;
 }
 public Place reverse(double latitude,double longitude) {
  if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new Failure(400,"Choose a valid map location");
  URI uri=UriComponentsBuilder.fromUriString(base).path("/reverse").queryParam("lat",latitude).queryParam("lon",longitude).queryParam("format","jsonv2").queryParam("addressdetails",1).build().encode().toUri();
  Place place=place(fetch(uri));if(place==null)throw new Failure(404,"No address was found for this pin. Try a nearby location.");return place;
 }
 private JsonNode fetch(URI uri) {
  String key=uri.toString();Instant now=clock.instant();Cached hit=cache.get(key);if(hit!=null&&hit.until.isAfter(now))return hit.value;
  synchronized(this){if(now.isBefore(nextCall))throw new Failure(429,"Please wait a moment before searching again");nextCall=now.plusMillis(1100);}
  try{JsonNode body=client.get().uri(uri).retrieve().body(JsonNode.class);if(body==null)throw new Failure(502,"Place search is unavailable. Try again or choose a saved building.");
   if(cache.size()>512)cache.clear();cache.put(key,new Cached(body,now.plusSeconds(600)));return body;
  }catch(Failure e){throw e;}catch(Exception e){throw new Failure(502,"Place search is unavailable. Try again or choose a saved building.");}
 }
 private static Place place(JsonNode row) {
  try{double lat=Double.parseDouble(row.path("lat").asText()),lon=Double.parseDouble(row.path("lon").asText());if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return null;
   JsonNode a=row.path("address");String display=row.path("display_name").asText("");String name=row.path("name").asText("");if(name.isBlank())name=display.split(",")[0];
   String city=first(a,"city","town","village","municipality","county");return new Place(name,display,lat,lon,city,a.path("state").asText(""),a.path("postcode").asText(""));
  }catch(RuntimeException e){return null;}
 }
 private static String first(JsonNode node,String...keys){for(String key:keys){String v=node.path(key).asText("");if(!v.isBlank())return v;}return "";}
}
