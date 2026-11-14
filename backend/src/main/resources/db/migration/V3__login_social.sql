-- Login com Google/Apple. email_verificado só vira true quando o provedor confirma o e-mail.
-- Contas criadas com senha começam como não verificadas (ainda não há confirmação por e-mail).
ALTER TABLE usuarios ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE identidades_sociais (
    id UUID PRIMARY KEY,
    provedor VARCHAR(10) NOT NULL CHECK (provedor IN ('GOOGLE', 'APPLE')),
    subject VARCHAR(255) NOT NULL,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT identidade_unica UNIQUE (provedor, subject)
);
CREATE INDEX identidades_usuario_idx ON identidades_sociais(usuario_id);
