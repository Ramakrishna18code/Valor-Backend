package com.valor.pincode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.valor.pincode.PincodeImportModels.*;

/**
 * Validates and classifies an offline proposal. It has no repository dependency,
 * performs no I/O, and cannot activate, deactivate, or persist a directory.
 */
final class PincodeImportService {
    private static final Pattern PIN = Pattern.compile("^[1-9][0-9]{5}$");
    private static final int STATE_MAX = 120, DISTRICT_MAX = 120, OFFICE_MAX = 160;

    Classification classify(Manifest manifest, List<InputRow> input, List<CurrentRow> current) {
        List<Rejected> rejected = new ArrayList<>();
        Map<String, InputRow> valid = new HashMap<>();
        List<InputRow> duplicates = new ArrayList<>();

        if (manifest == null || blank(manifest.sourceName()) || blank(manifest.datasetVersion())
                || manifest.sourceUpdatedAt() == null || manifest.kind() == null
                || (manifest.kind() == FileKind.COMPLETE_AUTHORITATIVE && !manifest.declaredComplete())
                || (manifest.kind() == FileKind.PARTIAL_CORRECTION && manifest.declaredComplete())) {
            throw new IllegalArgumentException("Manifest is incomplete or inconsistent");
        }
        for (InputRow row : input == null ? List.<InputRow>of() : input) {
            List<String> reasons = validate(row, manifest);
            String key = row == null ? null : key(row);
            if (reasons.isEmpty() && valid.containsKey(key)) {
                duplicates.add(row);
                reasons = List.of("Duplicate normalized (pincode, postOffice) key");
            }
            if (!reasons.isEmpty()) rejected.add(new Rejected(row, reasons));
            else valid.put(key, row);
        }

        Map<String, CurrentRow> existing = new HashMap<>();
        for (CurrentRow row : current == null ? List.<CurrentRow>of() : current) {
            String key = key(row.pincode(), row.postOffice());
            if (existing.put(key, row) != null) {
                throw new IllegalArgumentException("Current directory has duplicate normalized key: " + key);
            }
        }
        List<InputRow> inserted = new ArrayList<>(), updated = new ArrayList<>(), unchanged = new ArrayList<>();
        List<InputRow> validRows = new ArrayList<>(valid.values());
        validRows.sort(PincodeImportService::compareRows);
        for (InputRow row : validRows) {
            CurrentRow old = existing.get(key(row));
            if (old == null) inserted.add(row);
            else if (!sameContent(row, old)) updated.add(row);
            else unchanged.add(row);
        }
        List<InputRow> deactivation = new ArrayList<>();
        if (manifest.kind() == FileKind.COMPLETE_AUTHORITATIVE && rejected.isEmpty()) {
            for (CurrentRow old : existing.values()) {
                if (old.active() && !valid.containsKey(key(old.pincode(), old.postOffice()))) {
                    deactivation.add(new InputRow(old.pincode(), old.state(), old.district(), old.postOffice(),
                            old.locality(), old.sourceName(), old.sourceVersion(), old.sourceUpdatedAt()));
                }
            }
        }
        inserted.sort(PincodeImportService::compareRows);
        updated.sort(PincodeImportService::compareRows);
        unchanged.sort(PincodeImportService::compareRows);
        deactivation.sort(PincodeImportService::compareRows);
        duplicates.sort(PincodeImportService::compareRows);
        return new Classification(inserted, updated, unchanged, duplicates, deactivation, rejected, rejected.isEmpty());
    }

    private static List<String> validate(InputRow r, Manifest m) {
        List<String> errors = new ArrayList<>();
        if (r == null) return List.of("Row is null");
        if (r.pincode() == null || !PIN.matcher(r.pincode().trim()).matches()) errors.add("PIN must be six digits and cannot start with zero");
        required(errors, r.state(), "state", STATE_MAX); required(errors, r.district(), "district", DISTRICT_MAX);
        required(errors, r.postOffice(), "postOffice", OFFICE_MAX);
        if (r.locality() != null && r.locality().trim().length() > OFFICE_MAX) errors.add("locality exceeds 160 characters");
        if (m.sourceName().length() > 160) errors.add("sourceName exceeds 160 characters");
        if (m.datasetVersion().length() > 120) errors.add("sourceVersion exceeds 120 characters");
        if (r.sourceName() == null || r.sourceName().length() > 160) errors.add("sourceName exceeds 160 characters or is missing");
        if (r.sourceVersion() == null || r.sourceVersion().length() > 120) errors.add("sourceVersion exceeds 120 characters or is missing");
        if (r.sourceName() == null || r.sourceVersion() == null || !m.sourceName().equals(r.sourceName()) || !m.datasetVersion().equals(r.sourceVersion())) errors.add("row source metadata does not match manifest");
        if (r.sourceUpdatedAt() == null) errors.add("sourceUpdatedAt is required");
        else if (!m.sourceUpdatedAt().equals(r.sourceUpdatedAt())) errors.add("sourceUpdatedAt does not match manifest");
        return errors;
    }

    private static void required(List<String> errors, String value, String name, int max) {
        if (blank(value)) errors.add(name + " is required");
        else if (value.trim().length() > max) errors.add(name + " exceeds " + max + " characters");
    }
    private static boolean sameContent(InputRow r, CurrentRow c) {
        return eq(r.state(), c.state()) && eq(r.district(), c.district()) && eq(r.locality(), c.locality())
                && eq(r.sourceName(), c.sourceName()) && eq(r.sourceVersion(), c.sourceVersion())
                && eq(r.sourceUpdatedAt(), c.sourceUpdatedAt()) && c.active();
    }
    private static String key(InputRow r) { return key(r.pincode(), r.postOffice()); }
    private static String key(String pin, String office) { return norm(pin) + "\u0000" + norm(office); }
    private static int compareRows(InputRow a, InputRow b) {
        int result = key(a).compareTo(key(b));
        if (result != 0) return result;
        return String.valueOf(a.sourceVersion()).compareTo(String.valueOf(b.sourceVersion()));
    }
    private static String norm(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean eq(Object a, Object b) { return java.util.Objects.equals(a, b); }
}
