-- =====================================================================
-- Removes ONLY the AstroLog demo data (ids starting with "de000000-").
-- Real data uploaded from phones is never touched (its ids are random UUIDs).
-- Run in the Supabase dashboard → SQL Editor. Safe to run more than once.
-- =====================================================================

begin;

-- Observations go automatically (ON DELETE CASCADE), but delete them explicitly too in case a
-- demo observation was ever attached to a non-demo session.
delete from public.celestial_observations where id::text like 'de000000-%';
delete from public.observing_sessions     where id::text like 'de000000-%';

commit;

-- Check: both should be 0
select (select count(*) from public.observing_sessions     where id::text like 'de000000-%') as demo_sessions,
       (select count(*) from public.celestial_observations where id::text like 'de000000-%') as demo_observations;
