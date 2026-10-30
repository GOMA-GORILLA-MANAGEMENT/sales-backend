-- Wipes every table in the LOCAL development database so the new bar & restaurant schema starts clean.
-- There is no production data here. Run it against your local database only:
--
--   psql -h localhost -U eshopuser -d eshop -f scripts/reset-local-db.sql
--
-- Then start the backend with SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD set, and it recreates the tables
-- (spring.jpa.hibernate.ddl-auto=update) and your super admin.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO public;
