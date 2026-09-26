-- Schema privado: não adicionar "fisio" aos schemas expostos pela Data API do Supabase.
CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    senha_hash VARCHAR(100) NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE profissionais (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    especialidade VARCHAR(120) NOT NULL
);
CREATE TABLE horarios (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    profissional_id UUID NOT NULL REFERENCES profissionais(id),
    tipo_sessao VARCHAR(30) NOT NULL CHECK (tipo_sessao IN ('AVALIACAO', 'FISIOTERAPIA', 'PILATES')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DISPONIVEL', 'RESERVADO')),
    CONSTRAINT intervalo_valido CHECK (hora_fim > hora_inicio),
    CONSTRAINT horario_unico UNIQUE (profissional_id, data, hora_inicio)
);
CREATE TABLE agendamentos (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES usuarios(id),
    horario_id UUID NOT NULL REFERENCES horarios(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMADO', 'CANCELADO')),
    observacoes VARCHAR(1000)
);
CREATE UNIQUE INDEX reserva_ativa_unica ON agendamentos(horario_id) WHERE status = 'CONFIRMADO';
CREATE INDEX agendamentos_cliente_idx ON agendamentos(cliente_id);
CREATE INDEX horarios_busca_idx ON horarios(data, status, profissional_id, tipo_sessao);
CREATE TABLE recuperacoes_senha (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL
);
