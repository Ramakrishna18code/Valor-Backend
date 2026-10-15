package com.valor.pincode;

import com.valor.auth.AssetIdentityAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PincodeControllerTest {
    private final PincodeLookupClient repository = mock(PincodeLookupClient.class);
    private final AssetIdentityAccess identities = mock(AssetIdentityAccess.class);
    private final PincodeController controller = new PincodeController(repository, identities);

    @Test
    void rejectsMalformedPinBeforeDirectoryAccess() {
        var error = assertThrows(PincodeException.class, () -> controller.lookup("5000"));
        assertEquals(400, error.status);
        verifyNoInteractions(repository);
    }

    @Test
    void reportsUnknownValidPinAsNotFound() {
        when(repository.lookup("500001")).thenThrow(new PincodeException(404, "This PIN code was not found. Enter the address manually."));
        var error = assertThrows(PincodeException.class, () -> controller.lookup("500001"));
        assertEquals(404, error.status);
    }

    @Test
    void returnsAllPostOfficeRowsAndSourceMetadata() {
        var first = row("500001", "Telangana", "Hyderabad", "Ameerpet", "Ameerpet", "directory", "2026-1");
        var second = row("500001", "Telangana", "Hyderabad", "Punjagutta", "Punjagutta", "directory", "2026-1");
        when(repository.lookup("500001")).thenReturn(List.of(
                new PincodeDtos.Location("500001", "Telangana", "Hyderabad", "Ameerpet", "Ameerpet", "India Post API", "Hyderabad", null),
                new PincodeDtos.Location("500001", "Telangana", "Hyderabad", "Punjagutta", "Punjagutta", "India Post API", "Hyderabad", null)));

        var response = controller.lookup("500001");

        assertTrue(response.isSuccess());
        assertEquals("500001", response.getData().pincode());
        assertEquals(List.of("Ameerpet", "Punjagutta"), response.getData().locations().stream().map(PincodeDtos.Location::postOffice).toList());
        assertEquals("India Post API", response.getData().locations().getFirst().sourceName());
        verify(identities).actor();
    }

    private VerifiedPincode row(String pin, String state, String district, String office, String locality, String source, String version) {
        var row = new VerifiedPincode();
        row.pincode = pin; row.state = state; row.district = district; row.postOffice = office; row.locality = locality;
        row.sourceName = source; row.sourceVersion = version; row.sourceUpdatedAt = LocalDate.of(2026, 10, 1);
        row.verifiedAt = LocalDateTime.of(2026, 10, 2, 0, 0); row.active = true;
        return row;
    }
}
