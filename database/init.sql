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
  staff_name VARCHAR(80) NOT NULL,
  source VARCHAR(20) NOT NULL DEFAULT '原安排',
  absent TINYINT(1) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS staff (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_name VARCHAR(80) NOT NULL,
  department VARCHAR(80) NOT NULL,
  position VARCHAR(40) NOT NULL,
  skills VARCHAR(255) NOT NULL DEFAULT '',
  UNIQUE KEY uk_staff_dept_name (department, staff_name)
);

CREATE TABLE IF NOT EXISTS absence_fill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_date DATE NOT NULL,
  department VARCHAR(80) NOT NULL,
  position VARCHAR(40) NOT NULL,
  shift_name VARCHAR(40) NOT NULL,
  staff_name VARCHAR(80) NOT NULL,
  required_skills VARCHAR(255) NOT NULL DEFAULT '',
  reason VARCHAR(255) NOT NULL DEFAULT '',
  status VARCHAR(20) NOT NULL DEFAULT '待补位',
  replacement_staff_id BIGINT NULL,
  replacement_staff_name VARCHAR(80) NULL,
  initiated_by VARCHAR(80) NOT NULL,
  created_at VARCHAR(32) NOT NULL,
  processed_at VARCHAR(32) NULL,
  UNIQUE KEY uk_open_absence (schedule_date, department, shift_name, staff_name)
);

CREATE TABLE IF NOT EXISTS fill_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  absence_id BIGINT NOT NULL,
  department VARCHAR(80) NOT NULL,
  operator VARCHAR(80) NOT NULL,
  original_date DATE NOT NULL,
  original_shift VARCHAR(40) NOT NULL,
  original_position VARCHAR(40) NOT NULL,
  original_staff VARCHAR(80) NOT NULL,
  replacement_staff VARCHAR(80) NOT NULL,
  processed_at VARCHAR(32) NOT NULL,
  result VARCHAR(20) NOT NULL DEFAULT '补位成功',
  remark VARCHAR(255) NOT NULL DEFAULT '',
  KEY idx_fill_audit_absence (absence_id)
);

INSERT INTO schedule_snapshot (department, schedule_date, shift_name, staff_name)
VALUES ('急诊科', CURDATE(), '白班', '陈医生'), ('急诊科', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '夜班', '周护士');
