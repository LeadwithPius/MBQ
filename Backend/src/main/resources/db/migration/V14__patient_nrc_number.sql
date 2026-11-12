-- Store the patient's Zambian National Registration Card number.
-- Existing patient records remain valid; new adult registrations are validated by the API.
ALTER TABLE patient
    ADD COLUMN nrc_number VARCHAR(20);

CREATE UNIQUE INDEX uq_patient_nrc_number
    ON patient (UPPER(nrc_number))
    WHERE nrc_number IS NOT NULL;
