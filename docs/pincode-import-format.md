# PIN directory lookup roadmap

## Current implementation

Valor currently has a local database-backed lookup foundation: `PincodeController`,
`VerifiedPincode`, `VerifiedPincodeRepository`, the V42 `verified_pincodes` table,
and `PincodeDtos`. The Customer building flow calls the existing Backend lookup.
`PincodeImportService` is offline-only classification code with synthetic tests.

The local table and importer are preserved and unchanged in this planning phase.

## Proposed future lookup source

Use a small Backend adapter/proxy around this candidate endpoint:

`https://api.postalpincode.in/pincode/{PINCODE}`

The endpoint is not a proven dependency. Its response contract, uptime, usage
terms, rate limits, commercial permissions, attribution requirements, privacy
impact, and reliability require authorized verification before implementation.

The Backend adapter should normalize usable provider results into Valor's existing
lookup response contract. The Customer app should not depend directly on the
provider response schema.

## Expected behavior

- Validate six-digit PIN format before requesting the provider.
- Use a short timeout and bounded retry policy for retryable failures.
- Handle network errors, timeouts, rate limiting, malformed responses, empty
  results, and provider errors.
- Show all usable post-office choices and require explicit user selection when
  multiple results are returned.
- Allow manual address entry when lookup fails or returns no usable result.
- Autofilled state and district remain manually editable.
- The selected post office must not replace street, building, or landmark text.
- PIN lookup must never infer, replace, or update latitude/longitude.
- Preserve stale-response protection and manual address values.

## Data and operational scope

This roadmap does not include bulk dataset import/download, local provider data,
import-run tables, checksums, version activation, rollback machinery, scheduled
synchronization, or database writes caused by lookup. Service coverage and
Technician assignment are also out of scope.

The existing V42 schema, local lookup code, `PincodeImportService`, and tests
remain unchanged until a separately approved implementation change. Removing,
migrating, or deprecating those components requires its own review.

## Recovery and fallback

The simplest safe recovery is: local validation, Backend provider request,
bounded retry for retryable failures, clear failure state, preserved manual
fields, and continued manual address entry. Rare provider updates do not require
local refresh jobs under this approach.

## Required live verification

An authorized verification must test a representative valid PIN, including
`507003`, and record:

- HTTP status, response time, and exact JSON field types.
- Valid, invalid, empty, and multiple-post-office responses.
- Malformed-response behavior.
- Timeout, retry, rate-limit, and availability behavior.
- Terms for commercial use, storage, redistribution, and attribution.
- Whether Backend proxying is permitted.

This verification was not performed here. No external API call is part of this
roadmap update.

## Compatibility risks

Valor currently returns `pincode`, `state`, `district`, `postOffice`, `locality`,
`sourceName`, `sourceVersion`, and `sourceUpdatedAt`. Provider names, nullability,
status messages, and multiple-record behavior may differ. The adapter must
translate those differences without changing the Customer-facing contract unless
a separate contract review approves it.

Future `sourceName` and `sourceVersion` values must not imply legal approval or
local verification until those decisions are complete.

## Status

- External lookup adapter: proposed, pending live verification.
- Backend proxy: proposed and preferred for client consistency.
- Manual fallback: required future behavior.
- Local dataset import/version activation: explicitly out of scope.
- Existing V42/local importer: preserved and unchanged.
- MySQL production integration: unverified and must not be claimed as proven.
