package com.valor.pincode;

import static com.valor.pincode.PincodeImportModels.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PincodeImportServiceTest {
    private final PincodeImportService service = new PincodeImportService();
    private final Manifest complete = new Manifest(FileKind.COMPLETE_AUTHORITATIVE, "synthetic-fixture", "test-v1", LocalDate.of(2026, 1, 15), true);
    private final Manifest partial = new Manifest(FileKind.PARTIAL_CORRECTION, "synthetic-fixture", "test-v1", LocalDate.of(2026, 1, 15), false);

    @Test void classifiesInsertUpdateUnchangedAndDeactivation() {
        var input = List.of(row("500001", "Telangana", "Hyderabad", "Alpha Office", "Local", "synthetic-fixture", "test-v1"),
                row("500002", "Telangana", "Hyderabad", "New Office", null, "synthetic-fixture", "test-v1"));
        var current = List.of(current("500001", "Telangana", "Hyderabad", "Alpha Office", "Local", true),
                current("500003", "Telangana", "Hyderabad", "Old Office", null, true));
        var result = service.classify(complete, input, current);
        assertEquals(1, result.updated().size());
        assertEquals(1, result.inserted().size());
        assertEquals(1, result.deactivationCandidates().size());
    }

    @Test void whitespaceAndCaseDuplicateIsRejectedWithoutCollapse() {
        var a = row("500001", "State", "District", " Alpha   Office ", null, "synthetic-fixture", "test-v1");
        var b = row("500001", "State", "District", "alpha office", null, "synthetic-fixture", "test-v1");
        var result = service.classify(partial, List.of(a, b), List.of());
        assertEquals(1, result.duplicate().size());
        assertFalse(result.classificationValid());
    }

    @Test void partialFileNeverCreatesDeactivationCandidates() {
        var result = service.classify(partial, List.of(row("500001", "State", "District", "Office", null, "synthetic-fixture", "test-v1")),
                List.of(current("500999", "State", "District", "Existing Office", null, true)));
        assertTrue(result.deactivationCandidates().isEmpty());
        assertTrue(result.classificationValid());
    }

    @Test void invalidRowsMakeProposalUnsafeAndAreRejected() {
        var bad = row("000001", "", "District", "Office", null, "synthetic-fixture", "test-v1");
        var result = service.classify(complete, List.of(bad), List.of());
        assertEquals(1, result.rejected().size());
        assertFalse(result.classificationValid());
    }

    @Test void manifestMustDeclareCompleteAuthoritativeInput() {
        assertThrows(IllegalArgumentException.class, () -> service.classify(
                new Manifest(FileKind.COMPLETE_AUTHORITATIVE, "synthetic-fixture", "test-v1", LocalDate.now(), false), List.of(), List.of()));
    }

    @Test void invalidCompleteBatchHasNoDeactivationCandidates() {
        var result = service.classify(complete, List.of(row("000001", "", "District", "Bad", null, "synthetic-fixture", "test-v1")),
                List.of(current("500999", "State", "District", "Existing", null, true)));
        assertTrue(result.deactivationCandidates().isEmpty());
        assertFalse(result.classificationValid());
    }

    @Test void duplicateCompleteBatchHasNoDeactivationCandidates() {
        var a = row("500001", "State", "District", "Office", null, "synthetic-fixture", "test-v1");
        var b = row("500001", "State", "District", " office ", null, "synthetic-fixture", "test-v1");
        var result = service.classify(complete, List.of(a, b), List.of(current("500999", "State", "District", "Existing", null, true)));
        assertTrue(result.deactivationCandidates().isEmpty());
        assertFalse(result.classificationValid());
    }

    @Test void partialCannotClaimCompleteness() {
        assertThrows(IllegalArgumentException.class, () -> service.classify(
                new Manifest(FileKind.PARTIAL_CORRECTION, "synthetic-fixture", "test-v1", LocalDate.of(2026, 1, 15), true), List.of(), List.of()));
    }

    @Test void nullRowIsRejectedAndOtherRowsContinue() {
        var result = service.classify(partial, Arrays.asList(null, row("500001", "State", "District", "Office", null, "synthetic-fixture", "test-v1")), List.of());
        assertEquals(1, result.rejected().size());
        assertEquals(1, result.inserted().size());
    }

    @Test void sourceNameAndVersionLimitsAreEnforced() {
        var longName = "n".repeat(161);
        var longVersion = "v".repeat(121);
        var result = service.classify(new Manifest(FileKind.PARTIAL_CORRECTION, longName, longVersion, LocalDate.of(2026, 1, 15), false),
                List.of(row("500001", "State", "District", "Office", null, longName, longVersion)), List.of());
        assertFalse(result.classificationValid());
    }

    @Test void distinctOfficesForOnePinArePreserved() {
        var result = service.classify(partial, List.of(row("500001", "State", "District", "Alpha", null, "synthetic-fixture", "test-v1"),
                row("500001", "State", "District", "Beta", null, "synthetic-fixture", "test-v1")), List.of());
        assertEquals(2, result.inserted().size());
    }

    @Test void sourceMetadataChangeIsAnUpdate() {
        var input = row("500001", "State", "District", "Office", null, "synthetic-fixture", "test-v1");
        var old = new CurrentRow(1L, "500001", "State", "District", "Office", null, "old-source", "old-v1", LocalDate.of(2025, 1, 1), true);
        var result = service.classify(partial, List.of(input), List.of(old));
        assertEquals(1, result.updated().size());
        assertTrue(result.unchanged().isEmpty());
    }

    @Test void equivalentInputOrderProducesDeterministicResults() {
        var a = row("500002", "State", "District", "Beta", null, "synthetic-fixture", "test-v1");
        var b = row("500001", "State", "District", "Alpha", null, "synthetic-fixture", "test-v1");
        var first = service.classify(partial, List.of(a, b), List.of());
        var second = service.classify(partial, List.of(b, a), List.of());
        assertEquals(first.inserted(), second.inserted());
    }

    @Test void manifestAndRowDatesMustMatch() {
        var result = service.classify(partial, List.of(new InputRow("500001", "State", "District", "Office", null,
                "synthetic-fixture", "test-v1", LocalDate.of(2026, 1, 16))), List.of());
        assertFalse(result.classificationValid());
        assertEquals(1, result.rejected().size());
    }

    private static InputRow row(String pin, String state, String district, String office, String locality, String source, String version) {
        return new InputRow(pin, state, district, office, locality, source, version, LocalDate.of(2026, 1, 15));
    }
    private static CurrentRow current(String pin, String state, String district, String office, String locality, boolean active) {
        return new CurrentRow(1L, pin, state, district, office, locality, "old-source", "old-v1", LocalDate.of(2025, 1, 1), active);
    }
}
