-- Corrige columnas CHAR a VARCHAR para alinear con el mapeo de entidades Hibernate.
-- En PostgreSQL, CHAR(n) y VARCHAR(n) son compatibles en almacenamiento; no hay pérdida de datos.

ALTER TABLE facturador.contribuyentes      ALTER COLUMN ruc       TYPE VARCHAR(11);
ALTER TABLE facturador.contribuyentes      ALTER COLUMN ubigeo    TYPE VARCHAR(6);
ALTER TABLE facturador.comprobantes        ALTER COLUMN ruc_emisor TYPE VARCHAR(11);
ALTER TABLE facturador.series_correlativos ALTER COLUMN ruc_emisor TYPE VARCHAR(11);
