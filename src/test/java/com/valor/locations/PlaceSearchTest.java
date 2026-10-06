package com.valor.locations;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class PlaceSearchTest {
 HttpServer server; String base; AtomicInteger calls=new AtomicInteger(); TestClock clock=new TestClock();
 static class TestClock extends Clock { Instant now=Instant.parse("2026-10-06T00:00:00Z"); public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now;} void advance(){now=now.plusSeconds(2);} }
 @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();server.start();}
 @AfterEach void stop(){server.stop(0);}
 void respond(String path,int status,String body){server.createContext(path,exchange->{calls.incrementAndGet();assertEquals("Valor QA",exchange.getRequestHeaders().getFirst("User-Agent"));byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});}
 final String place="{\"name\":\"Tower\",\"display_name\":\"Tower, Hyderabad\",\"lat\":\"17.4\",\"lon\":\"78.5\",\"address\":{\"city\":\"Hyderabad\",\"state\":\"Telangana\",\"postcode\":\"500001\"}}";
 @Test void returnsValidCoordinatesAndCachesExplicitSearch(){respond("/search",200,"["+place+",{\"lat\":\"200\",\"lon\":\"0\"}]");var search=new PlaceSearch(base,"Valor QA",clock);var rows=search.search("Hyderabad tower");assertEquals(1,rows.size());assertEquals(17.4,rows.getFirst().latitude());assertEquals("Hyderabad",rows.getFirst().city());assertEquals(rows,search.search("Hyderabad tower"));assertEquals(1,calls.get());}
 @Test void globallyLimitsUncachedSearchAndAllowsCachedResults(){respond("/search",200,"["+place+"]");var search=new PlaceSearch(base,"Valor QA",clock);search.search("First tower");var error=assertThrows(PlaceSearch.Failure.class,()->search.search("Second tower"));assertEquals(429,error.status);assertEquals(1,search.search("First tower").size());clock.advance();search.search("Second tower");assertEquals(2,calls.get());}
 @Test void reverseAndValidation(){respond("/reverse",200,place);var search=new PlaceSearch(base,"Valor QA",clock);assertEquals("Tower",search.reverse(17.4,78.5).name());assertEquals(400,assertThrows(PlaceSearch.Failure.class,()->search.reverse(Double.NaN,0)).status);assertEquals(400,assertThrows(PlaceSearch.Failure.class,()->search.search("a")).status);assertEquals(1,calls.get());}
 @Test void sanitizesProviderFailure(){respond("/search",500,"private provider credentials");var search=new PlaceSearch(base,"Valor QA",clock);var error=assertThrows(PlaceSearch.Failure.class,()->search.search("Hyderabad"));assertEquals(502,error.status);assertFalse(error.getMessage().contains("credentials"));}
 @Test void reverseMissingAddressReturns404(){respond("/reverse",200,"{\"error\":\"Unable to geocode\"}");var search=new PlaceSearch(base,"Valor QA",clock);assertEquals(404,assertThrows(PlaceSearch.Failure.class,()->search.reverse(0,0)).status);}
}
