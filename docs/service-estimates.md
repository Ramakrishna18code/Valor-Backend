# Admin-managed test service estimates

The admin portal exposes Finance → Services & Pricing (`/finance/pricing`). Migration V60 seeds 41 services from the reference catalogue into a private draft. No estimates are published initially.

Deploy/restart the backend with V60 before using this screen. In the admin portal, edit entries and save drafts, then select Publish estimates. The technician app reads the published catalogue when opened or refreshed. Its local reference catalogue is no longer displayed as a fallback.

All displayed base prices exclude 18% GST. These estimates never change invoice amounts or create payment requests. AMC floor rates and package rates remain separate alternatives.

## API

- `GET /api/v1/admin/service-estimates`: admin draft.
- `PUT /api/v1/admin/service-estimates`: save `{version, items}`; ADMIN/SUPER_ADMIN only.
- `POST /api/v1/admin/service-estimates/publish`: publish `{version}`; ADMIN/SUPER_ADMIN only.
- `GET /api/v1/service-estimates`: authenticated read-only catalogue containing published active entries; available to customer clients as well, although this change only wires the technician UI.

Items contain `id`, `category`, `name`, `min`, `max`, `note`, and `active`. Null min/max means quotation required; min with null max means fixed estimate. Each successful mutation increments the version. Stale writes return 409. Changes are recorded in the existing audit log. Draft saves and publication are transactional.

Validation: `mvn -Dtest=EstimateCatalogueApiTest test`; admin `npm run build`; technician `npm run typecheck`. The API test covers migration seeding, role restrictions, draft isolation, active-only publication, stale-write conflicts and invalid price ranges.
