CREATE TABLE IF NOT EXISTS user_health_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary key',
    patient_id_hash VARCHAR(128) NOT NULL COMMENT 'patient identity hash, no plaintext id number',
    id_number_hash VARCHAR(128) DEFAULT NULL COMMENT 'compatible id number hash',
    gender VARCHAR(20) DEFAULT NULL COMMENT 'gender',
    birth_date DATE DEFAULT NULL COMMENT 'birth date',
    height_cm DECIMAL(5,2) DEFAULT NULL COMMENT 'height cm',
    weight_kg DECIMAL(5,2) DEFAULT NULL COMMENT 'weight kg',
    blood_type VARCHAR(20) DEFAULT NULL COMMENT 'blood type',
    special_status VARCHAR(255) DEFAULT NULL COMMENT 'special population status',
    source VARCHAR(50) DEFAULT NULL COMMENT 'source: user_confirmed, doctor_record, system_extract',
    confirmed TINYINT DEFAULT 0 COMMENT '0 unconfirmed, 1 confirmed',
    active TINYINT DEFAULT 1 COMMENT '1 active, 0 inactive',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    UNIQUE KEY uk_patient_profile (patient_id_hash),
    INDEX idx_profile_patient_active (patient_id_hash, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user long-term health profile';

CREATE TABLE IF NOT EXISTS user_long_term_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary key',
    patient_id_hash VARCHAR(128) NOT NULL COMMENT 'patient identity hash, no plaintext id number',
    memory_type VARCHAR(50) NOT NULL COMMENT 'allergy, family_history, past_history, chronic_disease, surgery_history, medication, special_status',
    memory_key VARCHAR(100) DEFAULT NULL COMMENT 'memory keyword',
    memory_value TEXT NOT NULL COMMENT 'memory content',
    severity VARCHAR(50) DEFAULT NULL COMMENT 'severity: mild, medium, severe',
    relation VARCHAR(50) DEFAULT NULL COMMENT 'family relation',
    evidence TEXT DEFAULT NULL COMMENT 'evidence or source description',
    source VARCHAR(50) DEFAULT NULL COMMENT 'source: user_confirmed, doctor_record, system_extract',
    confirmed TINYINT DEFAULT 0 COMMENT '0 unconfirmed, 1 confirmed',
    active TINYINT DEFAULT 1 COMMENT '1 active, 0 soft deleted',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    INDEX idx_patient_memory_type (patient_id_hash, memory_type),
    INDEX idx_patient_memory_active (patient_id_hash, active),
    INDEX idx_patient_memory_key (patient_id_hash, memory_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user long-term health memory detail';
