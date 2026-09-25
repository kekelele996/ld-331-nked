CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor VARCHAR(80) NOT NULL,
  action VARCHAR(120) NOT NULL,
  target VARCHAR(120) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS schedule_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  department VARCHAR(80) NOT NULL,
  schedule_date DATE NOT NULL,
  shift_name VARCHAR(40) NOT NULL,
  staff_name VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS absence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  department VARCHAR(80) NOT NULL,
  schedule_date DATE NOT NULL,
  shift_name VARCHAR(40) NOT NULL,
  staff_name VARCHAR(80) NOT NULL,
  position VARCHAR(80) NOT NULL,
  reason VARCHAR(200),
  status VARCHAR(20) NOT NULL DEFAULT '待补位',
  reported_by VARCHAR(80),
  substitute VARCHAR(80),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS backfill_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  absence_id BIGINT NOT NULL,
  department VARCHAR(80) NOT NULL,
  schedule_date DATE NOT NULL,
  shift_name VARCHAR(40) NOT NULL,
  original_staff VARCHAR(80) NOT NULL,
  original_shift VARCHAR(40) NOT NULL,
  substitute VARCHAR(80) NOT NULL,
  operator VARCHAR(80) NOT NULL,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schedule_snapshot (department, schedule_date, shift_name, staff_name)
VALUES ('急诊科', CURDATE(), '白班', '陈医生'), ('急诊科', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '夜班', '周护士');
