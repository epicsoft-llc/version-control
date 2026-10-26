-- An end-of-life answer outlived a cleared product and kept its date on the task list for good. Updating a
-- component now drops it; this removes the ones left behind before.
DELETE FROM eol_status
WHERE component_id IN (SELECT id FROM component WHERE eol_product IS NULL OR TRIM(eol_product) = '');
