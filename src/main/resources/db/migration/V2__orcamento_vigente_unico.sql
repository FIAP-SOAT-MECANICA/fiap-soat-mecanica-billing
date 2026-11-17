-- No maximo um orcamento vigente (GERADO ou APROVADO) por OS. Uma nova revisao so
-- pode nascer depois que a anterior for recusada, expirada ou cancelada.
CREATE UNIQUE INDEX uq_orcamentos_os_vigente ON orcamentos (os_id)
    WHERE estado IN ('GERADO', 'APROVADO');
