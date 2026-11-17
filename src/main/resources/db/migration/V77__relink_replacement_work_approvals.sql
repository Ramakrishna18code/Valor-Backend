-- A released replacement supersedes the quotation linked to an additional-work request.
-- Move pending requests to that replacement so customer acceptance can unblock repair.
UPDATE service_work_approvals work
SET quotation_id = (
    SELECT MAX(replacement.id)
    FROM service_quotations replacement
    WHERE replacement.supersedes_quotation_id = work.quotation_id
      AND replacement.service_request_id = work.service_request_id
      AND replacement.revision_kind = 'REPLACEMENT'
      AND replacement.status = 'ACCEPTED'
      AND replacement.release_status = 'RELEASED'
)
WHERE work.status = 'AWAITING_CUSTOMER'
  AND EXISTS (
    SELECT 1 FROM service_quotations previous
    WHERE previous.id = work.quotation_id
      AND previous.status = 'CANCELLED'
  )
  AND EXISTS (
    SELECT 1 FROM service_quotations replacement
    WHERE replacement.supersedes_quotation_id = work.quotation_id
      AND replacement.service_request_id = work.service_request_id
      AND replacement.revision_kind = 'REPLACEMENT'
      AND replacement.status = 'ACCEPTED'
      AND replacement.release_status = 'RELEASED'
  );

UPDATE service_work_approvals work
SET status = 'APPROVED'
WHERE work.status = 'AWAITING_CUSTOMER'
  AND EXISTS (
    SELECT 1 FROM service_quotations accepted
    WHERE accepted.id = work.quotation_id
      AND accepted.status = 'ACCEPTED'
  );
