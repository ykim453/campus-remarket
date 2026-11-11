CREATE TABLE IF NOT EXISTS student_campus_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    event_date DATE NOT NULL,
    category VARCHAR(12) NOT NULL,
    location VARCHAR(120) NOT NULL,
    audience VARCHAR(120) NOT NULL,
    INDEX ix_campus_event_date (school_id, event_date),
    CONSTRAINT fk_campus_event_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_campus_event_author FOREIGN KEY (author_member_id) REFERENCES member(id)
);
