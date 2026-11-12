CREATE TABLE orcamentos (
    id UUID PRIMARY KEY,
    os_id UUID NOT NULL,
    cliente_id UUID NOT NULL,
    revisao INTEGER NOT NULL CHECK (revisao > 0),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('GERADO','APROVADO','RECUSADO','EXPIRADO','CANCELADO')),
    valor_total NUMERIC(15,2) NOT NULL CHECK (valor_total > 0),
    itens_snapshot JSONB NOT NULL CHECK (jsonb_typeof(itens_snapshot) = 'array' AND jsonb_array_length(itens_snapshot) > 0),
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_orcamentos_id_os UNIQUE (id, os_id),
    CONSTRAINT uq_orcamentos_os_revisao UNIQUE (os_id, revisao),
    CONSTRAINT ck_orcamentos_expiracao CHECK (expira_em > criado_em)
);
CREATE INDEX ix_orcamentos_os ON orcamentos (os_id);

CREATE TABLE pagamentos (
    id UUID PRIMARY KEY,
    orcamento_id UUID NOT NULL,
    os_id UUID NOT NULL,
    referencia_externa VARCHAR(160) NOT NULL UNIQUE,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDENTE','APROVADO','RECUSADO')),
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    link_pagamento TEXT,
    criado_em TIMESTAMPTZ NOT NULL,
    decidido_em TIMESTAMPTZ,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pagamentos_orcamento_os FOREIGN KEY (orcamento_id, os_id) REFERENCES orcamentos(id, os_id),
    CONSTRAINT ck_pagamentos_decisao CHECK ((estado = 'PENDENTE') = (decidido_em IS NULL)),
    CONSTRAINT ck_pagamentos_tempo CHECK (decidido_em IS NULL OR decidido_em >= criado_em)
);
CREATE INDEX ix_pagamentos_os ON pagamentos (os_id, criado_em DESC);

CREATE TABLE webhook_eventos (
    provider_event_id VARCHAR(160) PRIMARY KEY,
    payment_id VARCHAR(160) NOT NULL,
    recebido_em TIMESTAMPTZ NOT NULL,
    processado_em TIMESTAMPTZ
);
CREATE INDEX ix_webhook_eventos_payment ON webhook_eventos (payment_id);

CREATE TABLE mensagem_processada (
    message_id UUID PRIMARY KEY,
    processada_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox (
    event_id UUID PRIMARY KEY,
    tipo VARCHAR(80) NOT NULL,
    envelope JSONB NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    publicado_em TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pendente ON outbox (criado_em) WHERE publicado_em IS NULL;
