-- Seed admin user
-- Password: admin123 (BCrypt encoded, cost 10)
INSERT INTO users (id, email, name, password, role, created_at, updated_at)
VALUES (nextval('user_id_seq'), 'admin@aasa.com', 'Admin', '$2a$10$ZhLoXudsw8o58IS.A2Y/8.BzJlKvZV66x.ks56mpOBKccd4tYZvce', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
