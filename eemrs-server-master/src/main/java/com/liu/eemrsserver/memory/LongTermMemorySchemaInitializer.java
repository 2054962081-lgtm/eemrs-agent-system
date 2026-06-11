package com.liu.eemrsserver.memory;

import org.apache.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class LongTermMemorySchemaInitializer implements ApplicationRunner {
    private static final Logger logger = Logger.getLogger(LongTermMemorySchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public LongTermMemorySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createHealthProfileTable();
            createLongTermMemoryTable();
            ensureHealthProfileColumns();
            ensureLongTermMemoryColumns();
            addIndexIfMissing(
                    "user_health_profile",
                    "uk_patient_profile",
                    "CREATE UNIQUE INDEX uk_patient_profile ON user_health_profile (patient_id_hash)"
            );
            addIndexIfMissing(
                    "user_health_profile",
                    "idx_profile_patient_active",
                    "CREATE INDEX idx_profile_patient_active ON user_health_profile (patient_id_hash, active)"
            );
            addIndexIfMissing(
                    "user_long_term_memory",
                    "idx_patient_memory_type",
                    "CREATE INDEX idx_patient_memory_type ON user_long_term_memory (patient_id_hash, memory_type)"
            );
            addIndexIfMissing(
                    "user_long_term_memory",
                    "idx_patient_memory_active",
                    "CREATE INDEX idx_patient_memory_active ON user_long_term_memory (patient_id_hash, active)"
            );
            addIndexIfMissing(
                    "user_long_term_memory",
                    "idx_patient_memory_key",
                    "CREATE INDEX idx_patient_memory_key ON user_long_term_memory (patient_id_hash, memory_key)"
            );
            logger.info("Long-term memory schema initialized");
        } catch (Exception e) {
            logger.warn("Long-term memory schema initialization skipped because database is unavailable", e);
        }
    }

    private void createHealthProfileTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS user_health_profile ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "patient_id_hash VARCHAR(128) NOT NULL,"
                        + "id_number_hash VARCHAR(128) DEFAULT NULL,"
                        + "gender VARCHAR(20) DEFAULT NULL,"
                        + "birth_date DATE DEFAULT NULL,"
                        + "height_cm DECIMAL(5,2) DEFAULT NULL,"
                        + "weight_kg DECIMAL(5,2) DEFAULT NULL,"
                        + "blood_type VARCHAR(20) DEFAULT NULL,"
                        + "special_status VARCHAR(255) DEFAULT NULL,"
                        + "source VARCHAR(50) DEFAULT NULL,"
                        + "confirmed TINYINT DEFAULT 0,"
                        + "active TINYINT DEFAULT 1,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user long-term health profile'"
        );
    }

    private void createLongTermMemoryTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS user_long_term_memory ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "patient_id_hash VARCHAR(128) NOT NULL,"
                        + "memory_type VARCHAR(50) NOT NULL,"
                        + "memory_key VARCHAR(100) DEFAULT NULL,"
                        + "memory_value TEXT NOT NULL,"
                        + "severity VARCHAR(50) DEFAULT NULL,"
                        + "relation VARCHAR(50) DEFAULT NULL,"
                        + "evidence TEXT DEFAULT NULL,"
                        + "source VARCHAR(50) DEFAULT NULL,"
                        + "confirmed TINYINT DEFAULT 0,"
                        + "active TINYINT DEFAULT 1,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user long-term health memory detail'"
        );
    }

    private void ensureHealthProfileColumns() {
        addColumnIfMissing("user_health_profile", "id_number_hash", "VARCHAR(128) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "gender", "VARCHAR(20) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "birth_date", "DATE DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "height_cm", "DECIMAL(5,2) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "weight_kg", "DECIMAL(5,2) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "blood_type", "VARCHAR(20) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "special_status", "VARCHAR(255) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "source", "VARCHAR(50) DEFAULT NULL");
        addColumnIfMissing("user_health_profile", "confirmed", "TINYINT DEFAULT 0");
        addColumnIfMissing("user_health_profile", "active", "TINYINT DEFAULT 1");
        addColumnIfMissing("user_health_profile", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing("user_health_profile", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void ensureLongTermMemoryColumns() {
        addColumnIfMissing("user_long_term_memory", "memory_type", "VARCHAR(50) NOT NULL");
        addColumnIfMissing("user_long_term_memory", "memory_key", "VARCHAR(100) DEFAULT NULL");
        addColumnIfMissing("user_long_term_memory", "memory_value", "TEXT NOT NULL");
        addColumnIfMissing("user_long_term_memory", "severity", "VARCHAR(50) DEFAULT NULL");
        addColumnIfMissing("user_long_term_memory", "relation", "VARCHAR(50) DEFAULT NULL");
        addColumnIfMissing("user_long_term_memory", "evidence", "TEXT DEFAULT NULL");
        addColumnIfMissing("user_long_term_memory", "source", "VARCHAR(50) DEFAULT NULL");
        addColumnIfMissing("user_long_term_memory", "confirmed", "TINYINT DEFAULT 0");
        addColumnIfMissing("user_long_term_memory", "active", "TINYINT DEFAULT 1");
        addColumnIfMissing("user_long_term_memory", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing("user_long_term_memory", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
        }
    }
}
