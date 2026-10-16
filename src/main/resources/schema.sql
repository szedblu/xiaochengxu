CREATE TABLE IF NOT EXISTS cf_users (
  id VARCHAR(36) PRIMARY KEY,
  openid VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(20) NOT NULL,
  account VARCHAR(30) NOT NULL UNIQUE,
  avatar VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS cf_sessions (
  token_hash CHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY(user_id) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_media (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  mime VARCHAR(30) NOT NULL,
  content LONGBLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_uploads (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  byte_size INT NOT NULL,
  part_count INT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY(user_id) REFERENCES cf_users(id),
  INDEX idx_upload_expiry(user_id,expires_at)
);
CREATE TABLE IF NOT EXISTS cf_upload_parts (
  upload_id VARCHAR(36) NOT NULL,
  part_index INT NOT NULL,
  content BLOB NOT NULL,
  PRIMARY KEY(upload_id,part_index),
  FOREIGN KEY(upload_id) REFERENCES cf_uploads(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS cf_profiles (
 user_id VARCHAR(36) PRIMARY KEY,
 since_date DATE NOT NULL,
 goal INT NOT NULL DEFAULT 2000,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (user_id) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_friends (
 user_id VARCHAR(36) NOT NULL,
 friend_id VARCHAR(36) NOT NULL,
 joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,friend_id),
 FOREIGN KEY (user_id) REFERENCES cf_users(id),
 FOREIGN KEY (friend_id) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_images (
 id VARCHAR(36) PRIMARY KEY,
 mime VARCHAR(30) NOT NULL,
 content LONGBLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS cf_recipes (
 id VARCHAR(36) PRIMARY KEY,
 owner_id VARCHAR(36) NULL,
 name VARCHAR(30) NOT NULL,
 category VARCHAR(10) NOT NULL,
 kcal INT NOT NULL,
 unit VARCHAR(10) NOT NULL,
 image_url VARCHAR(500) NOT NULL DEFAULT '',
 image_id VARCHAR(36) NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 FOREIGN KEY(owner_id) REFERENCES cf_users(id),
 FOREIGN KEY(image_id) REFERENCES cf_images(id)
);
CREATE TABLE IF NOT EXISTS cf_meals (
 owner_id VARCHAR(36) NOT NULL,
 meal_date DATE NOT NULL,
 meal_key CHAR(1) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 submitted_revision BIGINT NOT NULL DEFAULT -1,
 submitted_at TIMESTAMP NULL,
 PRIMARY KEY(owner_id,meal_date,meal_key),
 FOREIGN KEY(owner_id) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_lines (
 owner_id VARCHAR(36) NOT NULL,
 meal_date DATE NOT NULL,
 meal_key CHAR(1) NOT NULL,
 recipe_id VARCHAR(36) NOT NULL,
 qty INT NOT NULL,
 PRIMARY KEY(owner_id,meal_date,meal_key,recipe_id),
 FOREIGN KEY(owner_id,meal_date,meal_key) REFERENCES cf_meals(owner_id,meal_date,meal_key),
 FOREIGN KEY(recipe_id) REFERENCES cf_recipes(id)
);
CREATE TABLE IF NOT EXISTS cf_commands (
 actor_id VARCHAR(36) NOT NULL,
 command_id VARCHAR(36) NOT NULL,
 payload TEXT NOT NULL,
 PRIMARY KEY(actor_id,command_id),
 FOREIGN KEY(actor_id) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_orders (
 id VARCHAR(36) PRIMARY KEY,
 owner_id VARCHAR(36) NOT NULL,
 meal_date DATE NOT NULL,
 meal_key CHAR(1) NOT NULL,
 revision BIGINT NOT NULL,
 submitted_by VARCHAR(36) NOT NULL,
 submitted_name VARCHAR(20) NOT NULL,
 snapshot TEXT NOT NULL,
 total_kcal INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(owner_id,meal_date,meal_key,revision),
 FOREIGN KEY(owner_id) REFERENCES cf_users(id),
 FOREIGN KEY(submitted_by) REFERENCES cf_users(id)
);
CREATE TABLE IF NOT EXISTS cf_feedback (
 id VARCHAR(36) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 author_name VARCHAR(20) NOT NULL,
 feedback_type VARCHAR(10) NOT NULL,
 content VARCHAR(300) NOT NULL,
 contact VARCHAR(30) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES cf_users(id)
);
