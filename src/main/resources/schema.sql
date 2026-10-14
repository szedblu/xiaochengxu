CREATE TABLE IF NOT EXISTS qy_users (
  id VARCHAR(36) PRIMARY KEY,
  openid VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(20) NOT NULL,
  account VARCHAR(30) NOT NULL UNIQUE,
  intro VARCHAR(100) NOT NULL DEFAULT '',
  birthday DATE NULL,
  height_cm DECIMAL(4,1) NULL,
  goal_weight DECIMAL(4,1) NOT NULL DEFAULT 65.0,
  avatar VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS qy_sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_friends (
  user_id VARCHAR(36) NOT NULL,
  friend_id VARCHAR(36) NOT NULL,
  PRIMARY KEY (user_id, friend_id),
  FOREIGN KEY (user_id) REFERENCES qy_users(id),
  FOREIGN KEY (friend_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_posts (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  content TEXT NOT NULL,
  cover VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_likes (
  user_id VARCHAR(36) NOT NULL,
  post_id VARCHAR(36) NOT NULL,
  PRIMARY KEY (user_id, post_id),
  FOREIGN KEY (user_id) REFERENCES qy_users(id),
  FOREIGN KEY (post_id) REFERENCES qy_posts(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS qy_checkins (
  user_id VARCHAR(36) NOT NULL,
  entry_date DATE NOT NULL,
  PRIMARY KEY (user_id, entry_date),
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_schedules (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  title VARCHAR(30) NOT NULL,
  entry_type VARCHAR(16) NOT NULL,
  entry_date DATE NOT NULL,
  note VARCHAR(100) NOT NULL DEFAULT '',
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_weights (
  user_id VARCHAR(36) NOT NULL,
  entry_date DATE NOT NULL,
  weight DECIMAL(4,1) NOT NULL,
  PRIMARY KEY (user_id, entry_date),
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_ledger (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  entry_type VARCHAR(16) NOT NULL,
  category VARCHAR(20) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  note VARCHAR(100) NOT NULL DEFAULT '',
  entry_date DATETIME NOT NULL,
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);
CREATE TABLE IF NOT EXISTS qy_media (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  mime VARCHAR(30) NOT NULL,
  content LONGBLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES qy_users(id)
);

CREATE TABLE IF NOT EXISTS qy_uploads (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  byte_size INT NOT NULL,
  part_count INT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY (user_id) REFERENCES qy_users(id),
  INDEX idx_upload_expiry (user_id, expires_at)
);
CREATE TABLE IF NOT EXISTS qy_upload_parts (
  upload_id VARCHAR(36) NOT NULL,
  part_index INT NOT NULL,
  content BLOB NOT NULL,
  PRIMARY KEY (upload_id, part_index),
  FOREIGN KEY (upload_id) REFERENCES qy_uploads(id) ON DELETE CASCADE
);
