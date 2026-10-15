package com.valor.pincode;

import java.time.LocalDate;
import java.util.List;

/** Offline import records. These types deliberately do not write to the database. */
final class PincodeImportModels {
    private PincodeImportModels() {}

    enum FileKind { COMPLETE_AUTHORITATIVE, PARTIAL_CORRECTION }

    record Manifest(FileKind kind, String sourceName, String datasetVersion,
                    LocalDate sourceUpdatedAt, boolean declaredComplete) {}

    record InputRow(String pincode, String state, String district, String postOffice,
                    String locality, String sourceName, String sourceVersion,
                    LocalDate sourceUpdatedAt) {}

    record CurrentRow(Long id, String pincode, String state, String district,
                      String postOffice, String locality, String sourceName,
                      String sourceVersion, LocalDate sourceUpdatedAt, boolean active) {}

    record Rejected(InputRow row, List<String> reasons) {}

    record Classification(List<InputRow> inserted, List<InputRow> updated,
                         List<InputRow> unchanged, List<InputRow> duplicate,
                         List<InputRow> deactivationCandidates, List<Rejected> rejected,
                         boolean classificationValid) {}
}
