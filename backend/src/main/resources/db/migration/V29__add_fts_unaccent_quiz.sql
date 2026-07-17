CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE INDEX IF NOT EXISTS idx_bank_questions_fts 
ON bank_questions 
USING GIN (to_tsvector('simple', immutable_unaccent(coalesce(question_text, ''))));
