-- =====================================================================
-- Bulk synchronisation: POST /rest/v1/rpc/sync_observing_sessions
--
-- Body:  { "p_sessions": [ { session..., "observations": [ { observation... }, ... ] }, ... ] }
--
-- * SECURITY INVOKER: runs as the signed-in user, so every write is checked by RLS.
-- * Idempotent: sessions and observations are upserted by their client-generated UUIDs,
--   so retrying the same upload never creates duplicates.
-- * Observations deleted on the phone are deleted in the cloud (the payload is the
--   complete list of observations for that session).
-- * Partial failures: each session runs in its own sub-transaction (BEGIN ... EXCEPTION).
--   A bad session is rolled back on its own and reported in "failed"; the others are kept.
--
-- Returns:
--   { "synced_at": "...", "synced_count": 2, "failed_count": 1, "observation_count": 5,
--     "synced": [ { "id": "<uuid>", "observations": 3 }, ... ],
--     "failed": [ { "id": "<uuid or raw value>", "error_code": "missing_field", "message": "..." } ] }
-- =====================================================================

create or replace function public.sync_observing_sessions(p_sessions jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  c_max_sessions      constant int := 200;
  c_max_observations  constant int := 500;

  v_user_id     uuid := auth.uid();
  v_now         timestamptz := now();
  v_session     jsonb;
  v_obs         jsonb;
  v_raw_id      text;
  v_session_id  uuid;
  v_owner       uuid;
  v_obs_ids     uuid[];
  v_synced      jsonb := '[]'::jsonb;
  v_failed      jsonb := '[]'::jsonb;
  v_obs_total   int := 0;

  -- error details captured inside the per-session handler
  v_state       text;
  v_message     text;
  v_detail      text;
  v_column      text;
  v_constraint  text;
  v_code        text;
begin
  -- Whole-request checks: these fail the call with an HTTP error instead of a per-session result.
  if v_user_id is null then
    raise exception using errcode = '28000', message = 'not_authenticated',
      detail = 'Sign in first (anonymous sign-in is enough) and send the access token.';
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

  for v_session in select value from jsonb_array_elements(p_sessions) loop
    v_raw_id := case when jsonb_typeof(v_session) = 'object' then v_session ->> 'id' end;

    begin  -- sub-transaction for this session
      if jsonb_typeof(v_session) <> 'object' then
        raise exception using message = 'invalid_session', detail = 'Each session must be a JSON object.';
      end if;
      if v_raw_id is null then
        raise exception using message = 'missing_field', detail = 'Missing required field: id';
      end if;
      v_session_id := v_raw_id::uuid;            -- raises 22P02 for an invalid UUID

      -- A session id that already belongs to someone else is rejected with a clear message
      -- (RLS would block the write anyway).
      select s.user_id into v_owner from public.observing_sessions s where s.id = v_session_id;
      if found and v_owner <> v_user_id then
        raise exception using message = 'not_owner', detail = 'This session belongs to another user.';
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
        id, user_id, session_title, session_date, session_time, observing_location, duration_hours,
        description, sky_conditions, observer_name, equipment, bortle_scale, seeing_rating,
        latitude, longitude, synced_at)
      values (
        v_session_id, v_user_id,
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
        id, session_id, user_id, celestial_object, location_in_sky, observation_details,
        additional_comments, object_type, observed_time, has_photo, sort_order)
      select (o.value ->> 'id')::uuid,
             v_session_id,
             v_user_id,
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
      -- Everything this session wrote is rolled back; report why and carry on with the next one.
      get stacked diagnostics
        v_state      = returned_sqlstate,
        v_message    = message_text,
        v_detail     = pg_exception_detail,
        v_column     = column_name,
        v_constraint = constraint_name;

      v_code := case
        when v_state = 'P0001'            then v_message          -- our own validation errors above
        when v_state = '22P02'            then 'invalid_format'   -- bad UUID / number / boolean
        when v_state in ('22007', '22008') then 'invalid_date'
        when v_state = '22003'            then 'value_out_of_range'
        when v_state = '23502'            then 'missing_field'
        when v_state = '23514'            then 'invalid_value'
        when v_state in ('23505', '21000') then 'duplicate_id'
        when v_state = '42501'            then 'not_authorized'
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

comment on function public.sync_observing_sessions(jsonb) is
  'Uploads all sessions + observations of the signed-in user in one call. Idempotent; per-session results.';

revoke all on function public.sync_observing_sessions(jsonb) from public, anon;
grant execute on function public.sync_observing_sessions(jsonb) to authenticated;
