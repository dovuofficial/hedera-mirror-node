-- Change the values below if you are not installing via Docker (environment variable values come from .env file)
-- e.g. \set db_name='mydatabasename'
-- name of the database
\set db_name 'hederamirror'
--username
\set db_user 'hederamirror'
--user password
\set db_password 'mysecretpassword'
--owner of the database (usually postgres)
\set db_owner 'postgres'
--REST API user
\set api_user 'api'
--REST API password
\set api_password 'mysecretpassword'

-- Uncomment below if you are not installing via Docker
--
-- CREATE DATABASE :db_name
--      WITH
--      OWNER = :db_owner
--      CONNECTION LIMIT = -1;
--
-- CREATE USER :db_user WITH
--     LOGIN
--     NOCREATEDB
--     NOCREATEROLE
--     NOINHERIT
--     NOREPLICATION
--     CONNECTION LIMIT -1
--     PASSWORD ':db_password';

\c :db_name
