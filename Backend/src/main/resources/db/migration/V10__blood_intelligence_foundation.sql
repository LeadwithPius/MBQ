-- Keep legacy donor records unassigned until an administrator can verify their facility.
-- Non-admin users will only see donors explicitly assigned to their facility.
ALTER TABLE donor ADD COLUMN facility_id BIGINT REFERENCES facility(id) ON DELETE RESTRICT;
CREATE INDEX idx_donor_facility_group_eligible ON donor(facility_id, blood_group, eligible);

-- Newly received batches must pass a documented verification step before issue.
ALTER TABLE blood_stock_batch DROP CONSTRAINT chk_blood_batch_expiry_verification;
ALTER TABLE blood_stock_batch DROP CONSTRAINT blood_stock_batch_verification_status_check;
ALTER TABLE blood_stock_batch ADD CONSTRAINT chk_blood_batch_verification_status
    CHECK (verification_status IN ('VERIFIED', 'UNVERIFIED_LEGACY', 'PENDING_VERIFICATION'));
ALTER TABLE blood_stock_batch ADD CONSTRAINT chk_blood_batch_expiry_verification
    CHECK (
      (verification_status = 'UNVERIFIED_LEGACY' AND expiry_date IS NULL)
      OR
      (verification_status IN ('VERIFIED', 'PENDING_VERIFICATION') AND expiry_date IS NOT NULL)
    );

CREATE INDEX idx_blood_movement_stock_type_date
    ON blood_stock_movement(stock_id, movement_type, created_at);
