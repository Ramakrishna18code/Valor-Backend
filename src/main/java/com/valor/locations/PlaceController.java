package com.valor.locations;
import com.valor.auth.AssetIdentityAccess;
import com.valor.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
@RestController @RequestMapping("/api/v1/locations")
public class PlaceController {
 private final PlaceSearch places; private final AssetIdentityAccess identities;
 public PlaceController(PlaceSearch places, AssetIdentityAccess identities) { this.places=places; this.identities=identities; }
 @GetMapping("/search") public ApiResponse<List<PlaceSearch.Place>> search(@RequestParam String q) { identities.actor(); return ApiResponse.success("Places",places.search(q),200); }
 @GetMapping("/reverse") public ApiResponse<PlaceSearch.Place> reverse(@RequestParam double latitude,@RequestParam double longitude) { identities.actor(); return ApiResponse.success("Location",places.reverse(latitude,longitude),200); }
 @ExceptionHandler(PlaceSearch.Failure.class) ResponseEntity<ApiResponse<Object>> failure(PlaceSearch.Failure e) { return ResponseEntity.status(e.status).body(ApiResponse.error(e.getMessage(),e.status)); }
}
