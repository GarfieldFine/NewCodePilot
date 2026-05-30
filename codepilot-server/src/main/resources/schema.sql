-- CodePilot PR Review Assistant - Database Schema

-- Store each analysis run
CREATE TABLE IF NOT EXISTS analysis_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL UNIQUE,
    owner VARCHAR(128) NOT NULL,
    repo VARCHAR(128) NOT NULL,
    pr_number INT NOT NULL,
    pr_title VARCHAR(512),
    pr_url VARCHAR(1024),
    author VARCHAR(128),
    status VARCHAR(32) DEFAULT 'COMPLETED',
    total_findings INT DEFAULT 0,
    risk_score INT DEFAULT 0,
    token_usage JSON,
    analysis_result JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_owner_repo_pr (owner, repo, pr_number),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Store user feedback on individual rule findings
CREATE TABLE IF NOT EXISTS rule_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL,
    finding_id VARCHAR(128) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    file_name VARCHAR(512),
    line_number INT,
    feedback_type ENUM('CONFIRMED', 'FALSE_POSITIVE', 'MISSED_ISSUE') NOT NULL,
    comment VARCHAR(1024),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_analysis (analysis_id),
    INDEX idx_rule (rule_name),
    INDEX idx_feedback_type (feedback_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Store aggregated rule confidence statistics (updated on each feedback submission)
CREATE TABLE IF NOT EXISTS rule_confidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL UNIQUE,
    total_hits INT DEFAULT 0,
    confirmed INT DEFAULT 0,
    false_positives INT DEFAULT 0,
    missed_issues INT DEFAULT 0,
    precision_rate DECIMAL(5,4) DEFAULT 0.0000,
    recall_estimate DECIMAL(5,4) DEFAULT 0.0000,
    min_confidence_threshold DECIMAL(3,2) DEFAULT 0.50,
    enabled BOOLEAN DEFAULT TRUE,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default rule confidence records for existing 10 rules
INSERT IGNORE INTO rule_confidence (rule_name, min_confidence_threshold) VALUES
    ('SqlInjectionRule', 0.60),
    ('NullPointerRule', 0.40),
    ('RedisLockRule', 0.50),
    ('EmptyCatchRule', 0.30),
    ('ThreadLocalLeakRule', 0.55),
    ('HighIORule', 0.45),
    ('LargeTransactionRule', 0.50),
    ('BigObjectRule', 0.40),
    ('LongMethodRule', 0.35),
    ('MagicValueRule', 0.50);

-- Store evaluation dataset samples for measuring precision/recall
CREATE TABLE IF NOT EXISTS evaluation_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL,
    language VARCHAR(32) NOT NULL,
    title VARCHAR(256),
    code_snippet TEXT NOT NULL,
    should_match BOOLEAN NOT NULL,
    expected_severity VARCHAR(32),
    notes VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rule (rule_name),
    INDEX idx_language (language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
