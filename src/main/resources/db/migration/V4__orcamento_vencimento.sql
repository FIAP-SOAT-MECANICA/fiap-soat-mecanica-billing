-- The expiry job looks for budgets that are still GERADO and past their validity.
CREATE INDEX ix_orcamentos_vencimento ON orcamentos (expira_em) WHERE estado = 'GERADO';
