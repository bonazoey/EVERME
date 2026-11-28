BEGIN;
ALTER TABLE clone_lab.context ADD COLUMN IF NOT EXISTS source_message_id bigint REFERENCES clone_lab.session_messages(id);
ALTER TABLE clone_lab.context ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;
CREATE UNIQUE INDEX IF NOT EXISTS context_source_type_unique ON clone_lab.context(source_message_id,type);
UPDATE clone_lab.context SET is_active=false WHERE source_message_id IS NULL;
COMMIT;
