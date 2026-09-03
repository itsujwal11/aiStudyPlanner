-- Manual, development-only administrator provisioning.
--
-- This script is NOT executed automatically. Spring does not run it
-- (spring.sql.init is unset and the schema is built from the JPA entities by
-- ddl-auto=update), and docker-compose mounts docs/schema.sql - not this file -
-- into the database's init directory. Run it by hand only when you need an
-- administrator account.
--
-- It deliberately does NOT insert a hardcoded credential. An earlier version of
-- this file shipped a known BCrypt hash for a well-known address, which meant
-- every checkout of the repository shared the same admin password. Instead,
-- register the account normally through the application's sign-up flow so the
-- password is chosen by the operator and hashed by the application, then run
-- this statement once to promote it.
--
-- Usage:
--   1. Register a normal account through the UI (or POST /api/auth/register).
--   2. Replace the address below with that account's email.
--   3. psql -U aasa_user -d aasa_db -f seed_admin.sql

UPDATE users
SET role = 'ADMIN',
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'replace-with-your-registered-email@example.com';
