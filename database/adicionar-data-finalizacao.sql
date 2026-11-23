-- Migração manual opcional. Execute somente se data_finalizacao ainda não existir.
-- Verifique antes:
-- SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_SCHEMA = 'gerenciador_tarefas'
--   AND TABLE_NAME = 'tarefas' AND COLUMN_NAME = 'data_finalizacao';
-- Se a consulta retornar uma linha, NÃO execute o ALTER TABLE abaixo.
-- ddl-auto=update também pode adicionar essa coluna ao iniciar a aplicação.
-- Registros existentes permanecem preservados, com data_finalizacao NULL.

ALTER TABLE gerenciador_tarefas.tarefas
    ADD COLUMN data_finalizacao DATETIME(6) NULL;
