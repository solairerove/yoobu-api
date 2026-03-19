CREATE INDEX idx_audit_created_at_id ON audit_log(created_at DESC, id DESC);

CREATE INDEX idx_audit_tenant_created_at_id ON audit_log(tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_audit_action_created_at_id ON audit_log(action, created_at DESC, id DESC);
