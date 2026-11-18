-- A charge nobody decided can be withdrawn (CancelarCobranca). The decision-time check already covers it:
-- a cancelled charge records when it was cancelled, like any other decision.
ALTER TABLE pagamentos DROP CONSTRAINT pagamentos_estado_check;
ALTER TABLE pagamentos ADD CONSTRAINT ck_pagamentos_estado
    CHECK (estado IN ('PENDENTE','APROVADO','RECUSADO','CANCELADO'));
