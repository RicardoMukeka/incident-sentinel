--liquibase formatted sql

--changeset incidentsentinel:5
-- v2 (docs/ARCHITECTURE.md/§7): GitLab removido, retrieval determinístico de 3
-- fontes, 1 chamada de LLM sem tools, split de causa/solução, estrutura de custo.

-- ticket: campos da saída da investigação (sistema escreve, hipótese)
ALTER TABLE ticket CHANGE COLUMN confianca confianca_causa VARCHAR(10) NULL;
ALTER TABLE ticket ADD COLUMN causa_categoria VARCHAR(30) NULL AFTER causa_raiz;
ALTER TABLE ticket ADD COLUMN solucao_proposta TEXT NULL AFTER confianca_causa;
ALTER TABLE ticket ADD COLUMN confianca_solucao VARCHAR(10) NULL AFTER solucao_proposta;
ALTER TABLE ticket ADD COLUMN investigado_em DATETIME(6) NULL AFTER confianca_solucao;

-- ticket: registro de resolução (humano escreve, autoritativo) — v1 grava, não lê (corpus da v2)
ALTER TABLE ticket ADD COLUMN resolvido_em DATETIME(6) NULL;
ALTER TABLE ticket ADD COLUMN resolvido_por VARCHAR(150) NULL;
ALTER TABLE ticket ADD COLUMN avaliacao_causa VARCHAR(20) NULL;
ALTER TABLE ticket ADD COLUMN causa_efetiva TEXT NULL;
ALTER TABLE ticket ADD COLUMN avaliacao_solucao VARCHAR(20) NULL;
ALTER TABLE ticket ADD COLUMN solucao_efetiva TEXT NULL;

-- ticket: link_commit_relacionado sai junto com o GitLab (docs/ARCHITECTURE.md)
ALTER TABLE ticket DROP COLUMN link_commit_relacionado;

-- app_registry: gitlab_project_path sai junto com o GitLab (changelog-004 o adicionou)
ALTER TABLE app_registry DROP COLUMN gitlab_project_path;

-- investigation_budget: custo em US$ acumulado no dia, ao lado de tokens_acumulados (docs/ARCHITECTURE.md)
ALTER TABLE investigation_budget ADD COLUMN custo_acumulado_usd DECIMAL(12,4) NOT NULL DEFAULT 0;

-- investigation_llm_call: 1 linha por chamada de LLM (1 por investigação na v1, 2 na v2) — tokens +
-- custo estimado (snapshot do preço da época). A GCP/Vertex retorna tokens, NÃO dinheiro.
CREATE TABLE IF NOT EXISTS investigation_llm_call (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id BIGINT NOT NULL,
  agente VARCHAR(30) NOT NULL,
  model VARCHAR(60) NULL,
  prompt_tokens INT NULL,
  completion_tokens INT NULL,
  total_tokens INT NULL,
  custo_estimado_usd DECIMAL(12,6) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_llm_call_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
);

CREATE INDEX idx_llm_call_ticket ON investigation_llm_call (ticket_id);
CREATE INDEX idx_llm_call_created_at ON investigation_llm_call (created_at);
