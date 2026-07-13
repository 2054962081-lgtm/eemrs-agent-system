-- Development-only cleanup for duplicate login/profile rows.
-- Run this manually after confirming the backup tables are created successfully.

CREATE TABLE IF NOT EXISTS tb_patient_backup_before_dedupe AS
SELECT * FROM tb_patient;

CREATE TABLE IF NOT EXISTS tb_doctor_backup_before_dedupe AS
SELECT * FROM tb_doctor;

CREATE TABLE IF NOT EXISTS tb_guahao_backup_before_dedupe AS
SELECT * FROM tb_guahao;

DELETE p
FROM tb_patient p
JOIN (
    SELECT id_hash_code, MAX(id) AS keep_id
    FROM tb_patient
    WHERE id_hash_code IS NOT NULL AND id_hash_code <> ''
    GROUP BY id_hash_code
    HAVING COUNT(*) > 1
) d ON p.id_hash_code = d.id_hash_code
WHERE p.id <> d.keep_id;

DELETE d
FROM tb_doctor d
JOIN (
    SELECT id_hash_code, MAX(id) AS keep_id
    FROM tb_doctor
    WHERE id_hash_code IS NOT NULL AND id_hash_code <> ''
    GROUP BY id_hash_code
    HAVING COUNT(*) > 1
) dup ON d.id_hash_code = dup.id_hash_code
WHERE d.id <> dup.keep_id;

TRUNCATE TABLE tb_guahao;
