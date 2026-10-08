--liquibase formatted sql

--changeset incidentsentinel:2
-- Rollout v1 confirmado: 2 apps (docs/ARCHITECTURE.md e docs/architecture.md item 7).
-- service_name aqui assume o nome do app em produção — se o valor de
-- `service.name` reportado ao SigNoz divergir por ambiente (ex.: sufixo
-- -stg), ajustar antes de rodar em STG/dev.

INSERT INTO app_registry (service_name, display_name, ativo, criado_em)
VALUES ('service-orders', 'Agency Order TV', TRUE, UTC_TIMESTAMP(6));

INSERT INTO app_registry (service_name, display_name, ativo, criado_em)
VALUES ('service-checkout', 'Valida Compra TV', TRUE, UTC_TIMESTAMP(6));
