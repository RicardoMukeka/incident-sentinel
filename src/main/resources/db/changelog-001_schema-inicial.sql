--liquibase formatted sql

--changeset incidentsentinel:1
-- Schema inicial (docs/ARCHITECTURE.md) — 4 tabelas, sem tabela de watermark
-- (derivado de error_signal_log) nem shedlock.

CREATE TABLE IF NOT EXISTS app_registry (
  service_name VARCHAR(150) NOT NULL,
  display_name VARCHAR(150) NULL,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em DATETIME(6) NOT NULL,
  ultima_execucao_inicio DATETIME(6) NULL,
  ultima_execucao_fim DATETIME(6) NULL,
  ultima_execucao_status VARCHAR(20) NULL,
  PRIMARY KEY (service_name)
);

CREATE TABLE IF NOT EXISTS error_signal_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  app VARCHAR(150) NOT NULL,
  tipo_erro VARCHAR(50) NOT NULL,
  chave_dedup CHAR(64) NOT NULL,
  timestamp DATETIME(6) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  CONSTRAINT fk_error_signal_log_app FOREIGN KEY (app) REFERENCES app_registry (service_name)
);

CREATE INDEX idx_error_signal_log_app_tipo_ts ON error_signal_log (app, tipo_erro, timestamp);

CREATE TABLE IF NOT EXISTS investigation_budget (
  dia DATE NOT NULL,
  contador BIGINT NOT NULL DEFAULT 0,
  tokens_acumulados BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (dia)
);

CREATE TABLE IF NOT EXISTS ticket (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  app VARCHAR(150) NOT NULL,
  tipo_erro VARCHAR(50) NOT NULL,
  chave_dedup CHAR(64) NOT NULL,
  data DATE NOT NULL,
  status VARCHAR(50) NOT NULL,
  causa_raiz TEXT NULL,
  confianca VARCHAR(10) NULL,
  fontes_consultadas JSON NULL,
  primeira_ocorrencia_ts DATETIME(6) NOT NULL,
  ultima_ocorrencia_ts DATETIME(6) NOT NULL,
  contagem_ocorrencias INT NOT NULL DEFAULT 1,
  link_commit_relacionado VARCHAR(500) NULL,
  orcamento_debitado BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT uk_ticket_dedup UNIQUE (app, tipo_erro, chave_dedup, data),
  CONSTRAINT fk_ticket_app FOREIGN KEY (app) REFERENCES app_registry (service_name)
);

CREATE INDEX idx_ticket_status ON ticket (status);
CREATE INDEX idx_ticket_data ON ticket (data);
