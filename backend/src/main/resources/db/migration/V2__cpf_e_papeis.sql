-- Papéis de acesso e CPF vinculado a cada conta.
-- O CPF nunca é guardado em texto puro: cpf_cifrado (AES-GCM) e cpf_hash (HMAC, só para impedir duplicidade).
-- As colunas de CPF aceitam nulo para contas criadas antes desta versão, que informam o CPF no próximo agendamento.
ALTER TABLE usuarios ADD COLUMN papel VARCHAR(10) NOT NULL DEFAULT 'CLIENTE' CHECK (papel IN ('CLIENTE', 'ADMIN'));
ALTER TABLE usuarios ADD COLUMN cpf_cifrado VARCHAR(200);
ALTER TABLE usuarios ADD COLUMN cpf_hash VARCHAR(64);
ALTER TABLE usuarios ADD CONSTRAINT cpf_completo CHECK ((cpf_cifrado IS NULL) = (cpf_hash IS NULL));
CREATE UNIQUE INDEX usuarios_cpf_hash_unico ON usuarios(cpf_hash) WHERE cpf_hash IS NOT NULL;
