-- Spring Batch 5.1 PostgreSQL repository contract (INT-ARCH A1).
CREATE TABLE interest_batch.batch_job_instance (
 job_instance_id BIGINT PRIMARY KEY, version BIGINT, job_name VARCHAR(100) NOT NULL,
 job_key VARCHAR(32) NOT NULL, UNIQUE(job_name, job_key)
);
CREATE TABLE interest_batch.batch_job_execution (
 job_execution_id BIGINT PRIMARY KEY, version BIGINT,
 job_instance_id BIGINT NOT NULL REFERENCES interest_batch.batch_job_instance,
 create_time TIMESTAMP NOT NULL, start_time TIMESTAMP, end_time TIMESTAMP,
 status VARCHAR(10), exit_code VARCHAR(2500), exit_message VARCHAR(2500), last_updated TIMESTAMP
);
CREATE TABLE interest_batch.batch_job_execution_params (
 job_execution_id BIGINT NOT NULL REFERENCES interest_batch.batch_job_execution,
 parameter_name VARCHAR(100) NOT NULL, parameter_type VARCHAR(100) NOT NULL,
 parameter_value VARCHAR(2500), identifying CHAR(1) NOT NULL
);
CREATE TABLE interest_batch.batch_step_execution (
 step_execution_id BIGINT PRIMARY KEY, version BIGINT NOT NULL, step_name VARCHAR(100) NOT NULL,
 job_execution_id BIGINT NOT NULL REFERENCES interest_batch.batch_job_execution,
 create_time TIMESTAMP NOT NULL, start_time TIMESTAMP, end_time TIMESTAMP,
 status VARCHAR(10), commit_count BIGINT, read_count BIGINT, filter_count BIGINT,
 write_count BIGINT, read_skip_count BIGINT, write_skip_count BIGINT,
 process_skip_count BIGINT, rollback_count BIGINT, exit_code VARCHAR(2500),
 exit_message VARCHAR(2500), last_updated TIMESTAMP
);
CREATE TABLE interest_batch.batch_step_execution_context (
 step_execution_id BIGINT PRIMARY KEY REFERENCES interest_batch.batch_step_execution,
 short_context VARCHAR(2500) NOT NULL, serialized_context TEXT
);
CREATE TABLE interest_batch.batch_job_execution_context (
 job_execution_id BIGINT PRIMARY KEY REFERENCES interest_batch.batch_job_execution,
 short_context VARCHAR(2500) NOT NULL, serialized_context TEXT
);
CREATE SEQUENCE interest_batch.batch_step_execution_seq MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE interest_batch.batch_job_execution_seq MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE interest_batch.batch_job_seq MAXVALUE 9223372036854775807 NO CYCLE;
GRANT USAGE ON SCHEMA interest_batch TO interest_repository;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA interest_batch TO interest_repository;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA interest_batch TO interest_repository;
