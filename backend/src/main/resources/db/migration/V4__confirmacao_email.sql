-- Link de confirmação de e-mail: só o hash do token é guardado, uso único, validade de 24 horas.
CREATE TABLE confirmacoes_email (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL
);
