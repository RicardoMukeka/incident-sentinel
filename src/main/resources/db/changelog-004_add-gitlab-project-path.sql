--liquibase formatted sql

--changeset incidentsentinel:4
-- gitlab_project_path (docs/ARCHITECTURE.md/§8.3) — projeto GitLab correspondente ao app, cadastrado
-- manualmente junto com a linha em app_registry (mesma "única ação necessária" pra um app entrar em
-- monitoramento). Usado pra restringir deterministicamente as tools do GitLab na investigação
-- (GitlabProjectScopedToolCallback) — sem esse campo preenchido, nenhuma tool do GitLab é oferecida
-- ao ChatClient pra esse app, em vez de liberar sem escopo. NULL por padrão: nenhum valor é
-- inventado aqui pros apps já existentes (service-orders, service-checkout, changelog-002).

ALTER TABLE app_registry ADD COLUMN gitlab_project_path VARCHAR(255) NULL;
