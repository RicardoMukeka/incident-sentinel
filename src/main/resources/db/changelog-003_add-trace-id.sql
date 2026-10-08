--liquibase formatted sql

--changeset incidentsentinel:3
-- trace_id (docs/ARCHITECTURE.md) — capturado na coleta (evento discreto), propagado até o
-- ticket que abriu a ocorrência (nunca sobrescrito em ON DUPLICATE KEY UPDATE, mesmo padrão de
-- primeira_ocorrencia_ts). Nome exato do atributo na resposta do SigNoz ("traceID") não confirmado
-- contra STG — ver SignozQueryTemplateFactory. Sempre NULL para LATENCIA_ANOMALA (agregação, sem
-- span causador único).

ALTER TABLE error_signal_log ADD COLUMN trace_id VARCHAR(128) NULL;
ALTER TABLE ticket ADD COLUMN trace_id VARCHAR(128) NULL;
