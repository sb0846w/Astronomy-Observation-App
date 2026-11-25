-- =====================================================================
-- Search: POST /rest/v1/rpc/search_observations
--
-- Body: { "p_query": "jupiter", "p_limit": 20, "p_offset": 0 }
--
-- * Case-insensitive, partial matching on celestial_object ("jup" finds "Jupiter").
-- * Exact name matches come first, then newest sessions first.
-- * Each row contains the observation and its session details, plus total_count
--   (the number of matches ignoring limit/offset) for pagination.
-- * SECURITY INVOKER + STABLE: read-only, and RLS still applies.
-- =====================================================================

create or replace function public.search_observations(
  p_query  text,
  p_limit  integer default 20,
  p_offset integer default 0)
returns table (
  observation_id       uuid,
  celestial_object     text,
  object_type          text,
  location_in_sky      text,
  observation_details  text,
  additional_comments  text,
  observed_time        text,
  has_photo            boolean,
  session_id           uuid,
  session_title        text,
  session_date         date,
  session_time         text,
  observing_location   text,
  observer_name        text,
  equipment            text,
  sky_conditions       text,
  total_count          bigint)
language plpgsql
stable
security invoker
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_query   text := btrim(coalesce(p_query, ''));
  v_pattern text;
begin
  if v_query = '' then
    raise exception using errcode = '22023', message = 'search_query_required',
      detail = 'Type at least one character to search for.';
  end if;
  if char_length(v_query) > 100 then
    raise exception using errcode = '22023', message = 'search_query_too_long',
      detail = 'Search text can be at most 100 characters.';
  end if;
  if p_limit is null or p_limit < 1 or p_limit > 100 then
    raise exception using errcode = '22023', message = 'invalid_limit',
      detail = 'p_limit must be between 1 and 100.';
  end if;
  if p_offset is null or p_offset < 0 then
    raise exception using errcode = '22023', message = 'invalid_offset',
      detail = 'p_offset must be 0 or more.';
  end if;

  -- Escape LIKE wildcards so "%" or "_" typed by the user are searched literally.
  v_pattern := '%' || replace(replace(replace(v_query, '\', '\\'), '%', '\%'), '_', '\_') || '%';

  return query
    select o.id, o.celestial_object, o.object_type, o.location_in_sky, o.observation_details,
           o.additional_comments, o.observed_time, o.has_photo,
           s.id, s.session_title, s.session_date, s.session_time, s.observing_location,
           s.observer_name, s.equipment, s.sky_conditions,
           count(*) over () as total_count
      from public.celestial_observations o
      join public.observing_sessions s on s.id = o.session_id
     where o.celestial_object ilike v_pattern
     order by (lower(o.celestial_object) = lower(v_query)) desc,
              s.session_date desc, s.session_time desc, o.sort_order
     limit p_limit offset p_offset;
end;
$$;

comment on function public.search_observations(text, integer, integer) is
  'Case-insensitive partial search of celestial observations, with session details and pagination.';

revoke all on function public.search_observations(text, integer, integer) from public;
grant execute on function public.search_observations(text, integer, integer) to anon, authenticated;
