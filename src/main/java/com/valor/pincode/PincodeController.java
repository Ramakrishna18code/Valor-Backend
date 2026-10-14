package com.valor.pincode;
import com.valor.auth.AssetIdentityAccess; import com.valor.response.ApiResponse; import io.swagger.v3.oas.annotations.Operation; import java.util.*; import java.util.regex.Pattern; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/locations/pincodes") class PincodeController {
 private static final Pattern PIN=Pattern.compile("^[1-9][0-9]{5}$"); private final VerifiedPincodeRepository pincodes; private final AssetIdentityAccess identities;
 PincodeController(VerifiedPincodeRepository pincodes,AssetIdentityAccess identities){this.pincodes=pincodes;this.identities=identities;}
 @Operation(operationId="lookupVerifiedPincode") @GetMapping("/{pincode}") ApiResponse<PincodeDtos.Lookup> lookup(@PathVariable String pincode){
  identities.actor(); if(pincode==null||!PIN.matcher(pincode).matches())throw new PincodeException(400,"Enter a valid six-digit Indian PIN code");
  var locations=pincodes.findByPincodeAndActiveTrueOrderByPostOfficeAsc(pincode).stream().map(r->new PincodeDtos.Location(r.pincode,r.state,r.district,r.postOffice,r.locality,r.sourceName,r.sourceVersion,r.sourceUpdatedAt)).toList();
  if(locations.isEmpty())throw new PincodeException(404,"This PIN code is not available in the verified directory"); return ApiResponse.success("Verified PIN code",new PincodeDtos.Lookup(pincode,locations),200);
 }
}
class PincodeException extends RuntimeException { final int status; PincodeException(int status,String message){super(message);this.status=status;} }
