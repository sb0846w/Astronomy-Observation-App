-- =====================================================================
-- No-login access model (agreed for the coursework: no Supabase Auth, no login,
-- registration or logout).
--
-- * Both apps use ONLY the publishable key (database role "anon").
-- * Everyone can READ sessions and observations (the MAUI app needs this).
-- * Nobody can INSERT / UPDATE / DELETE the tables directly.
-- * The only write path is sync_observing_sessions(), which validates every field.
-- * Ownership without accounts: each phone generates a secret "device key" once and
--   sends it with every upload. The first upload of a session stores the key's SHA-256
--   hash in a private table (not reachable through the API). Later uploads of that
--   session must send the same key, so another phone cannot overwrite it even though
--   session ids are public.
--
-- This replaces profiles / user_id / anonymous sign-in from the earlier migrations.
-- =====================================================================

-- ---------------------------------------------------------------- 1. remove the auth-based pieces

drop policy if exists "Users can create their own sessions"                   on public.observing_sessions;
drop policy if exists "Users can update their own sessions"                   on public.observing_sessions;
drop policy if exists "Users can delete their own sessions"                   on public.observing_sessions;
drop policy if exists "Users can add observations to their own sessions"      on public.celestial_observations;
drop policy if exists "Users can update observations in their own sessions"   on public.celestial_observations;
drop policy if exists "Users can delete their own observations"               on public.celestial_observations;

drop function if exists public.sync_observing_sessions(jsonb);

drop trigger  if exists on_auth_user_created       on auth.users;
drop trigger  if exists on_auth_user_email_changed on auth.users;
drop function if exists private.sync_profile_from_auth_user();

-- (session_id, user_id) foreign key -> plain session_id foreign key
alter table public.celestial_observations drop constraint if exists celestial_observations_session_fkey;
alter table public.celestial_observations drop column if exists user_id;          -- also drops its index
alter table public.observing_sessions     drop constraint if exists observing_sessions_id_user_key;
alter table public.observing_sessions     drop column if exists user_id;          -- also drops its index and FK
drop table if exists public.profiles;

alter table public.celestial_observations
  add constraint celestial_observations_session_id_fkey
  foreign key (session_id) references public.observing_sessions (id) on delete cascade;

drop index if exists public.celestial_observations_session_id_idx;
create index celestial_observations_session_id_idx
  on public.celestial_observations (session_id, sort_order);

-- ---------------------------------------------------------------- 2. table privileges

-- Read-only for the apps. (The "Anyone can read …" SELECT policies from the RLS migration stay.)
revoke insert, update, delete, truncate on public.observing_sessions, public.celestial_observations from anon, authenticated;
grant select on public.observing_sessions, public.celestial_observations to anon, authenticated;

-- ---------------------------------------------------------------- 3. device keys (private)

create table private.session_device_keys (
  session_id       uuid primary key references public.observing_sessions (id) on delete cascade,
  device_key_hash  text not null,                     -- hex SHA-256 of the phone's secret key
  created_at       timestamptz not null default now()
);

comment on table private.session_device_keys is
  'Which device (by hashed secret key) first uploaded each session. Not exposed through the API.';

alter table private.session_device_keys enable row level security;   -- no policies: no API access at all
revoke all on private.session_device_keys from public, anon, authenticated;

-- ---------------------------------------------------------------- 4. bulk upload function

-- POST /rest/v1/rpc/sync_observing_sessions
-- Body: { "p_device_key": "<uuid kept on the phone>", "p_sessions": [ { session..., "observations": [...] } ] }
--
-- SECURITY DEFINER: the "anon" role has no write privileges on the tables, so this
-- function writes on its behalf after validating everything. search_path is empty so
-- every object is schema-qualified (prevents search_path hijacking).
--
-- * Idempotent: rows are upserted by their client-generated UUIDs.
-- * Observations removed on the phone are removed in the cloud.
-- * Each session runs in its own sub-transaction: a bad session is rolled back and
--   reported in "failed"; the other sessions are kept.
create or replace function public.sync_observing_sessions(p_device_key text, p_sessions jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  c_max_sessions      constant int := 200;
  c_max_observations  constant int := 500;

  v_key_hash    text;
  v_owner_hash  text;
  v_now         timestamptz := now();
  v_session     jsonb;
  v_obs         jsonb;
  v_raw_id      text;
  v_session_id  uuid;
  v_obs_ids     uuid[];
  v_synced      jsonb := '[]'::jsonb;
  v_failed      jsonb := '[]'::jsonb;
  v_obs_total   int := 0;

  v_state       text;
  v_message     text;
  v_detail      text;
  v_column      text;
  v_constraint  text;
  v_code        text;
begin
  -- ---- whole-request checks (HTTP 400 with code 22023) ----
  if p_device_key is null or p_device_key !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception using errcode = '22023', message = 'invalid_device_key',
      detail = 'p_device_key must be the UUID the app generated and stored on this phone.';
  end if;
  if p_sessions is null or jsonb_typeof(p_sessions) <> 'array' then
    raise exception using errcode = '22023', message = 'invalid_payload',
      detail = 'p_sessions must be a JSON array of sessions.';
  end if;
  if jsonb_array_length(p_sessions) = 0 then
    raise exception using errcode = '22023', message = 'empty_payload',
      detail = 'There are no sessions to sync.';
  end if;
  if jsonb_array_length(p_sessions) > c_max_sessions then
    raise exception using errcode = '22023', message = 'too_many_sessions',
      detail = format('At most %s sessions can be synced in one request.', c_max_sessions);
  end if;

  v_key_hash := encode(sha256(convert_to(lower(p_device_key), 'UTF8')), 'hex');

  for v_session in select value from jsonb_array_elements(p_sessions) loop
    v_raw_id := case when jsonb_typeof(v_session) = 'object' then v_session ->> 'id' end;

    begin  -- sub-transaction for this session
      if jsonb_typeof(v_session) <> 'object' then
        raise exception using message = 'invalid_session', detail = 'Each session must be a JSON object.';
      end if;
      if v_raw_id is null then
        raise exception using message = 'missing_field', detail = 'Missing required field: id';
      end if;
      v_session_id := v_raw_id::uuid;             -- raises 22P02 for an invalid UUID

      -- Ownership: an existing session may only be changed with the key that first uploaded it.
      select k.device_key_hash into v_owner_hash
        from private.session_device_keys k where k.session_id = v_session_id;
      if (found and v_owner_hash <> v_key_hash)
         or (not found and exists (select 1 from public.observing_sessions s where s.id = v_session_id)) then
        raise exception using message = 'not_owner',
          detail = 'This session was uploaded from another device.';
      end if;

      v_obs := coalesce(v_session -> 'observations', '[]'::jsonb);
      if jsonb_typeof(v_obs) <> 'array' then
        raise exception using message = 'invalid_observations', detail = 'observations must be a JSON array.';
      end if;
      if jsonb_array_length(v_obs) > c_max_observations then
        raise exception using message = 'too_many_observations',
          detail = format('A session can have at most %s observations.', c_max_observations);
      end if;

      -- 1. Upsert the session
      insert into public.observing_sessions (
        id, session_title, session_date, session_time, observing_location, duration_hours,
        description, sky_conditions, observer_name, equipment, bortle_scale, seeing_rating,
        latitude, longitude, synced_at)
      values (
        v_session_id,
        btrim(v_session ->> 'session_title'),
        (v_session ->> 'session_date')::date,
        btrim(v_session ->> 'session_time'),
        btrim(v_session ->> 'observing_location'),
        (v_session ->> 'duration_hours')::double precision,
        nullif(btrim(v_session ->> 'description'), ''),
        nullif(btrim(v_session ->> 'sky_conditions'), ''),
        nullif(btrim(v_session ->> 'observer_name'), ''),
        nullif(btrim(v_session ->> 'equipment'), ''),
        (v_session ->> 'bortle_scale')::smallint,
        (v_session ->> 'seeing_rating')::smallint,
        (v_session ->> 'latitude')::double precision,
        (v_session ->> 'longitude')::double precision,
        v_now)
      on conflict (id) do update set
        session_title      = excluded.session_title,
        session_date       = excluded.session_date,
        session_time       = excluded.session_time,
        observing_location = excluded.observing_location,
        duration_hours     = excluded.duration_hours,
        description        = excluded.description,
        sky_conditions     = excluded.sky_conditions,
        observer_name      = excluded.observer_name,
        equipment          = excluded.equipment,
        bortle_scale       = excluded.bortle_scale,
        seeing_rating      = excluded.seeing_rating,
        latitude           = excluded.latitude,
        longitude          = excluded.longitude,
        synced_at          = excluded.synced_at;

      -- first upload of this session: remember which device owns it
      insert into private.session_device_keys (session_id, device_key_hash)
      values (v_session_id, v_key_hash)
      on conflict (session_id) do nothing;

      -- 2. Observation ids sent for this session (raises 22P02 for an invalid UUID)
      select coalesce(array_agg((o.value ->> 'id')::uuid), '{}')
        into v_obs_ids
        from jsonb_array_elements(v_obs) as o(value);

      if array_position(v_obs_ids, null) is not null then
        raise exception using message = 'missing_field', detail = 'Missing required field: observations[].id';
      end if;
      if exists (
        select 1 from public.celestial_observations c
        where c.id = any (v_obs_ids) and c.session_id <> v_session_id
      ) then
        raise exception using message = 'observation_id_conflict',
          detail = 'An observation id in this session is already used by a different session.';
      end if;

      -- 3. Remove observations that were deleted on the phone
      delete from public.celestial_observations c
       where c.session_id = v_session_id
         and c.id <> all (v_obs_ids);

      -- 4. Upsert the observations, keeping the phone's order
      insert into public.celestial_observations (
        id, session_id, celestial_object, location_in_sky, observation_details,
        additional_comments, object_type, observed_time, has_photo, sort_order)
      select (o.value ->> 'id')::uuid,
             v_session_id,
             btrim(o.value ->> 'celestial_object'),
             btrim(o.value ->> 'location_in_sky'),
             btrim(o.value ->> 'observation_details'),
             nullif(btrim(o.value ->> 'additional_comments'), ''),
             nullif(btrim(o.value ->> 'object_type'), ''),
             nullif(btrim(o.value ->> 'observed_time'), ''),
             coalesce((o.value ->> 'has_photo')::boolean, false),
             (o.ord - 1)::int
        from jsonb_array_elements(v_obs) with ordinality as o(value, ord)
      on conflict (id) do update set
        celestial_object    = excluded.celestial_object,
        location_in_sky     = excluded.location_in_sky,
        observation_details = excluded.observation_details,
        additional_comments = excluded.additional_comments,
        object_type         = excluded.object_type,
        observed_time       = excluded.observed_time,
        has_photo           = excluded.has_photo,
        sort_order          = excluded.sort_order;

      v_synced := v_synced || jsonb_build_object('id', v_session_id, 'observations', jsonb_array_length(v_obs));
      v_obs_total := v_obs_total + jsonb_array_length(v_obs);

    exception when others then
      -- Everything this session wrote is rolled back; report why and carry on.
      get stacked diagnostics
        v_state      = returned_sqlstate,
        v_message    = message_text,
        v_detail     = pg_exception_detail,
        v_column     = column_name,
        v_constraint = constraint_name;

      v_code := case
        when v_state = 'P0001'             then v_message          -- our own checks above
        when v_state = '22P02'             then 'invalid_format'   -- bad UUID / number / boolean
        when v_state in ('22007', '22008') then 'invalid_date'
        when v_state = '22003'             then 'value_out_of_range'
        when v_state = '23502'             then 'missing_field'
        when v_state = '23514'             then 'invalid_value'
        when v_state in ('23505', '21000') then 'duplicate_id'
        else 'database_error'
      end;

      v_failed := v_failed || jsonb_build_object(
        'id', v_raw_id,
        'error_code', v_code,
        'message', case
          when v_state = 'P0001' then v_detail
          when v_state = '23502' then format('Missing required field: %s', v_column)
          when v_state = '23514' then format('A value is not allowed (rule %s).', v_constraint)
          when v_state = '21000' then 'The same observation id appears twice in this session.'
          else v_message
        end);
    end;
  end loop;

  return jsonb_build_object(
    'synced_at',         v_now,
    'synced_count',      jsonb_array_length(v_synced),
    'failed_count',      jsonb_array_length(v_failed),
    'observation_count', v_obs_total,
    'synced',            v_synced,
    'failed',            v_failed);
end;
$$;

comment on function public.sync_observing_sessions(text, jsonb) is
  'Uploads all sessions + observations from one phone in one call. Idempotent; per-session results; device-key ownership.';

revoke all on function public.sync_observing_sessions(text, jsonb) from public;
grant execute on function public.sync_observing_sessions(text, jsonb) to anon, authenticated;
