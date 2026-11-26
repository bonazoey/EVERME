-- DESIGN SUPPLEMENT ONLY: not a migration, never run on the connected project DB.
-- Apply after generating the schema from everme.dbml in an isolated validation DB.
-- DBML expresses PK/FK/UNIQUE/CHECK, but cannot express deferred constraint triggers.

ALTER TABLE conversation_sessions ADD CONSTRAINT ck_session_kind_clone CHECK (
    (kind = 'personal' AND clone_id IS NULL)
    OR (kind = 'spokesperson' AND clone_id IS NOT NULL)
);

-- A fixed kind in each child + composite FK prevents mixed types.
-- A deferred check additionally requires exactly one matching detail at COMMIT.
CREATE FUNCTION check_knowledge_detail_exists() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target_id uuid;
    target_kind information_kind;
    detail_table text;
    found_count bigint;
BEGIN
    IF TG_TABLE_NAME = 'knowledge_versions' THEN
        IF TG_OP = 'DELETE' THEN target_id := OLD.id; ELSE target_id := NEW.id; END IF;
    ELSE
        IF TG_OP = 'DELETE' THEN target_id := OLD.version_id; ELSE target_id := NEW.version_id; END IF;
    END IF;
    SELECT kind INTO target_kind FROM knowledge_versions WHERE id = target_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    detail_table := CASE target_kind
        WHEN 'FACT' THEN 'fact_details'
        WHEN 'MEMORY' THEN 'memory_details'
        WHEN 'VALUE' THEN 'value_details'
        WHEN 'PREFERENCE' THEN 'preference_details'
        WHEN 'BEHAVIOR' THEN 'behavior_details'
    END;
    EXECUTE format('SELECT count(*) FROM %I WHERE version_id = $1', detail_table)
        INTO found_count USING target_id;
    IF found_count <> 1 THEN
        RAISE EXCEPTION 'A knowledge version must have one matching detail: %', target_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER knowledge_detail_required
AFTER INSERT OR UPDATE ON knowledge_versions
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_knowledge_detail_exists();

CREATE CONSTRAINT TRIGGER fact_detail_required
AFTER INSERT OR UPDATE OR DELETE ON fact_details
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_knowledge_detail_exists();
CREATE CONSTRAINT TRIGGER memory_detail_required
AFTER INSERT OR UPDATE OR DELETE ON memory_details
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_knowledge_detail_exists();
CREATE CONSTRAINT TRIGGER value_detail_required
AFTER INSERT OR UPDATE OR DELETE ON value_details
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_knowledge_detail_exists();
CREATE CONSTRAINT TRIGGER preference_detail_required
AFTER INSERT OR UPDATE OR DELETE ON preference_details
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_knowledge_detail_exists();
CREATE CONSTRAINT TRIGGER behavior_detail_required
AFTER INSERT OR UPDATE OR DELETE ON behavior_details
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION check_knowledge_detail_exists();

CREATE FUNCTION protect_knowledge_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'knowledge_items' THEN
        IF ROW(NEW.owner_id, NEW.kind, NEW.semantic_key)
            IS DISTINCT FROM ROW(OLD.owner_id, OLD.kind, OLD.semantic_key) THEN
            RAISE EXCEPTION 'Knowledge identity is immutable' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF ROW(NEW.item_id, NEW.kind, NEW.version_no, NEW.content_ciphertext,
               NEW.encryption_key_id, NEW.sensitivity, NEW.recorded_at)
            IS DISTINCT FROM ROW(OLD.item_id, OLD.kind, OLD.version_no, OLD.content_ciphertext,
               OLD.encryption_key_id, OLD.sensitivity, OLD.recorded_at) THEN
            RAISE EXCEPTION 'Create a new knowledge version instead of overwriting'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_knowledge_item BEFORE UPDATE ON knowledge_items
FOR EACH ROW EXECUTE FUNCTION protect_knowledge_identity();
CREATE TRIGGER immutable_knowledge_version BEFORE UPDATE ON knowledge_versions
FOR EACH ROW EXECUTE FUNCTION protect_knowledge_identity();

CREATE FUNCTION check_source_owner() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM knowledge_versions v
        JOIN knowledge_items k ON k.id = v.item_id
        JOIN analysis_jobs j ON j.id = NEW.analysis_job_id AND j.owner_id = k.owner_id
        JOIN message_revisions r ON r.id = NEW.message_revision_id
        JOIN messages m ON m.id = r.message_id AND m.role = 'user'
        JOIN conversation_sessions s ON s.id = m.session_id
        WHERE v.id = NEW.version_id AND s.kind = 'personal'
          AND s.participant_id = k.owner_id
    ) THEN
        RAISE EXCEPTION 'Source must be an owner personal/user utterance'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER source_owner_required BEFORE INSERT OR UPDATE ON knowledge_sources
FOR EACH ROW EXECUTE FUNCTION check_source_owner();

CREATE FUNCTION check_analysis_owner() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM message_revisions r
        JOIN messages m ON m.id = r.message_id AND m.role = 'user'
        JOIN conversation_sessions s ON s.id = m.session_id
        WHERE r.id = NEW.source_revision_id AND s.kind = 'personal'
          AND s.participant_id = NEW.owner_id
    ) THEN
        RAISE EXCEPTION 'Analysis requires an owner personal/user utterance'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER analysis_owner_required BEFORE INSERT OR UPDATE ON analysis_jobs
FOR EACH ROW EXECUTE FUNCTION check_analysis_owner();

-- No HNSW DDL here until the embedding model/dimension is fixed.
-- The trigger supplement does not replace runtime authentication/authorization,
-- lease CAS, source active checks, output checks or encryption-key management.

CREATE FUNCTION reject_knowledge_detail_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Preserve version details; deactivate the version instead'
            USING ERRCODE = '23514';
    END IF;
    IF NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'Create a new knowledge version/detail instead of rewriting'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER immutable_fact_detail BEFORE UPDATE OR DELETE ON fact_details
FOR EACH ROW EXECUTE FUNCTION reject_knowledge_detail_rewrite();
CREATE TRIGGER immutable_memory_detail BEFORE UPDATE OR DELETE ON memory_details
FOR EACH ROW EXECUTE FUNCTION reject_knowledge_detail_rewrite();
CREATE TRIGGER immutable_value_detail BEFORE UPDATE OR DELETE ON value_details
FOR EACH ROW EXECUTE FUNCTION reject_knowledge_detail_rewrite();
CREATE TRIGGER immutable_preference_detail BEFORE UPDATE OR DELETE ON preference_details
FOR EACH ROW EXECUTE FUNCTION reject_knowledge_detail_rewrite();
CREATE TRIGGER immutable_behavior_detail BEFORE UPDATE OR DELETE ON behavior_details
FOR EACH ROW EXECUTE FUNCTION reject_knowledge_detail_rewrite();

CREATE FUNCTION check_conflict_item() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM knowledge_versions a JOIN knowledge_versions b ON a.item_id = b.item_id
        WHERE a.id = NEW.existing_version_id AND b.id = NEW.incoming_version_id
    ) THEN
        RAISE EXCEPTION 'Conflicting versions must belong to the same item'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER conflict_item_required BEFORE INSERT OR UPDATE ON knowledge_conflicts
FOR EACH ROW EXECUTE FUNCTION check_conflict_item();
