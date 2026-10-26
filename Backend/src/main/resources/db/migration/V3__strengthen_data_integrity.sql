ALTER TABLE blood_stock
    ADD CONSTRAINT chk_blood_stock_units_nonnegative
    CHECK (units_available >= 0);

-- A low-stock threshold cannot be negative.
ALTER TABLE blood_stock
    ADD CONSTRAINT chk_blood_stock_threshold_nonnegative
    CHECK (low_stock_threshold >= 0);

-- Department names must contain meaningful text.
ALTER TABLE department
    ADD CONSTRAINT chk_department_name_not_blank
    CHECK (LENGTH(TRIM(name)) > 0);

-- Ticket numbers must contain meaningful text.
ALTER TABLE queue_ticket
    ADD CONSTRAINT chk_queue_ticket_number_not_blank
    CHECK (LENGTH(TRIM(ticket_number)) > 0);

-- A facility should not have duplicate department names,
-- ignoring differences in capitalization and surrounding spaces.
CREATE UNIQUE INDEX uq_department_facility_name_normalized
    ON department (facility_id, LOWER(TRIM(name)));