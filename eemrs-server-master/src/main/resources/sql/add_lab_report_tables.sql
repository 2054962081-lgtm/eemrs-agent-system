CREATE TABLE IF NOT EXISTS tb_lab_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_id_hash_code VARCHAR(255) NOT NULL,
    report_token VARCHAR(255) NOT NULL,
    report_payload_cipher TEXT,
    department_cipher VARCHAR(512),
    report_time_ope DECIMAL(65, 0),
    report_type_cipher VARCHAR(512),
    image_cipher_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lab_report_token (report_token),
    KEY idx_lab_report_patient_hash (patient_id_hash_code),
    KEY idx_lab_report_time_ope (report_time_ope)
);

CREATE TABLE IF NOT EXISTS tb_lab_report_search_index (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    index_token VARCHAR(255) NOT NULL,
    report_token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lab_report_search_index (index_token, report_token),
    KEY idx_lab_report_search_index_token (index_token),
    KEY idx_lab_report_search_index_report_token (report_token)
);
