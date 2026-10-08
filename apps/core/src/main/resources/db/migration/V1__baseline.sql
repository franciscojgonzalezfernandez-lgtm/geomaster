-- Baseline migration. Flyway in apps/core is the single owner of the database schema (ADR-0005).
CREATE EXTENSION IF NOT EXISTS vector;
