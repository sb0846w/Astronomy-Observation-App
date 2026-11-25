-- Removes the first prototype backend (sessions / observations / upload_sessions),
-- which had no user ownership. It only ever held demo data.

drop function if exists public.upload_sessions(jsonb);
drop table if exists public.observations;
drop table if exists public.sessions;
