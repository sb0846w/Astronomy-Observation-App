-- =====================================================================
-- AstroLog backend tests (no-login model)
--
-- Run in the Supabase dashboard (SQL Editor). Every check runs as the role the apps use
-- ("anon" = the publishable key), with two different device keys acting as two phones.
-- Everything happens inside ONE transaction that is rolled back at the end by raising an
-- exception that carries the report, so it is safe to run against the live project.
--
-- Expected output:  ERROR: BACKEND TESTS: 25 passed, 0 failed  (followed by the list)
-- Test data uses its own UUIDs (e1e2e3e4-... / f1f2f3f4-...), separate from the sample data.
-- =====================================================================
do $tests$
declare
  key_a    text := '11111111-aaaa-4aaa-8aaa-111111111111';   -- phone A
  key_b    text := '22222222-bbbb-4bbb-8bbb-222222222222';   -- phone B
  sess1    uuid := 'e1e2e3e4-0001-4000-8000-000000000001';
  sess2    uuid := 'e1e2e3e4-0002-4000-8000-000000000002';
  payload  jsonb := $json$[
    {"id":"e1e2e3e4-0001-4000-8000-000000000001","session_title":"Winter planets from the park",
     "session_date":"2026-11-20","session_time":"20:30","observing_location":"Greenwich Park, London",
     "duration_hours":2,"description":"Quick session after work.","sky_conditions":"Mostly clear",
     "observer_name":"Demo Observer","equipment":"Telescope","bortle_scale":8,"seeing_rating":3,
     "latitude":51.4769,"longitude":-0.0005,
     "observations":[
       {"id":"f1f2f3f4-0001-4000-8000-000000000001","celestial_object":"Jupiter","object_type":"Planet",
        "location_in_sky":"Gemini, east","observation_details":"Two cloud belts, four moons.","observed_time":"20:45"},
       {"id":"f1f2f3f4-0001-4000-8000-000000000002","celestial_object":"Moon","object_type":"Moon",
        "location_in_sky":"South-east, low","observation_details":"Waxing gibbous.","observed_time":"21:10","has_photo":true},
       {"id":"f1f2f3f4-0001-4000-8000-000000000003","celestial_object":"Saturn","object_type":"Planet",
        "location_in_sky":"Aquarius, south-west","observation_details":"Rings almost edge-on.","observed_time":"21:30"}]},
    {"id":"e1e2e3e4-0002-4000-8000-000000000002","session_title":"Dark sky trip",
     "session_date":"2026-11-15","session_time":"22:00","observing_location":"Galloway Forest, Scotland",
     "duration_hours":3.5,"observer_name":"Demo Observer","equipment":"Binoculars","bortle_scale":2,"seeing_rating":4,
     "observations":[
       {"id":"f1f2f3f4-0002-4000-8000-000000000001","celestial_object":"Andromeda Galaxy (M31)","object_type":"Galaxy",
        "location_in_sky":"Andromeda, high in the south","observation_details":"Long oval glow."},
       {"id":"f1f2f3f4-0002-4000-8000-000000000002","celestial_object":"Orion Nebula (M42)","object_type":"Nebula",
        "location_in_sky":"Orion's sword","observation_details":"Fuzzy patch around the Trapezium."},
       {"id":"f1f2f3f4-0002-4000-8000-000000000003","celestial_object":"Jupiter","object_type":"Planet",
        "location_in_sky":"Gemini, east","observation_details":"Small bright disc."}]}
  ]$json$::jsonb;
  res      jsonb;
  n        bigint;
  t        bigint;
  txt      text;
  report   text := '';
  passed   int := 0;
  failed   int := 0;
  ok       boolean;
  tname    text;
begin
  -- Act exactly like the apps: the publishable key = database role "anon"
  perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
  execute 'set local role anon';

  -- ------------------------------------------------------------------ uploads from phone A
  res := public.sync_observing_sessions(key_a, payload);
  tname := 'T01 bulk sync: 2 sessions, 6 observations, 0 failures';
  ok := res->>'synced_count' = '2' and res->>'failed_count' = '0' and res->>'observation_count' = '6';
  report := report || format(E'\n[%s] %s  %s', case when ok then 'PASS' else 'FAIL' end, tname, case when ok then '' else res::text end);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  res := public.sync_observing_sessions(key_a, payload);
  select count(*) into n from public.observing_sessions where id in (sess1, sess2);
  select count(*) into t from public.celestial_observations where session_id in (sess1, sess2);
  tname := 'T02 idempotent retry: still 2 sessions / 6 observations';
  ok := n = 2 and t = 6;
  report := report || format(E'\n[%s] %s  (%s/%s)', case when ok then 'PASS' else 'FAIL' end, tname, n, t);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  res := public.sync_observing_sessions(key_a,
           jsonb_set(payload, '{0,observations}', (payload #> '{0,observations}') - 2));
  select count(*) into n from public.celestial_observations where session_id = sess1;
  tname := 'T03 observation removed on phone is removed in cloud (3 -> 2)';
  ok := n = 2;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;
  res := public.sync_observing_sessions(key_a, payload);   -- put it back

  res := public.sync_observing_sessions(key_a, jsonb_build_array(
    payload -> 1,
    jsonb_build_object('id', 'c0000000-0000-4000-8000-000000000001', 'session_date', '2026-11-01',
                       'session_time', '20:00', 'observing_location', 'X', 'duration_hours', 1),
    jsonb_build_object('id', 'not-a-uuid', 'session_title', 'T', 'session_date', '2026-11-01',
                       'session_time', '20:00', 'observing_location', 'X', 'duration_hours', 1),
    jsonb_build_object('id', 'c0000000-0000-4000-8000-000000000002', 'session_title', 'T', 'session_date', '2026-02-30',
                       'session_time', '20:00', 'observing_location', 'X', 'duration_hours', 1),
    jsonb_build_object('id', 'c0000000-0000-4000-8000-000000000003', 'session_title', 'T', 'session_date', '2026-11-01',
                       'session_time', '20:00', 'observing_location', 'X', 'duration_hours', 1, 'bortle_scale', 12)));
  select string_agg(f ->> 'error_code', ',' order by f ->> 'error_code') into txt
    from jsonb_array_elements(res -> 'failed') f;
  tname := 'T04 partial failure: 1 synced, 4 failed with clear codes';
  ok := res->>'synced_count' = '1' and txt = 'invalid_date,invalid_format,invalid_value,missing_field';
  report := report || format(E'\n[%s] %s  (%s)', case when ok then 'PASS' else 'FAIL' end, tname, txt);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  tname := 'T05 failed sessions leave nothing behind';
  ok := not exists (select 1 from public.observing_sessions where id::text like 'c0000000-%');
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  res := public.sync_observing_sessions(key_a, jsonb_build_array(jsonb_set(payload -> 0, '{observations}',
           jsonb_build_array(payload #> '{0,observations,0}', payload #> '{0,observations,0}'))));
  select count(*) into n from public.celestial_observations where session_id = sess1;
  tname := 'T06 duplicate observation id rejected, session unchanged';
  ok := res #>> '{failed,0,error_code}' = 'duplicate_id' and n = 3;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  res := public.sync_observing_sessions(key_a, jsonb_build_array(jsonb_set(payload -> 1, '{observations}',
           jsonb_build_array(payload #> '{0,observations,0}'))));
  tname := 'T07 observation id from another session rejected';
  ok := res #>> '{failed,0,error_code}' = 'observation_id_conflict';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    res := public.sync_observing_sessions(key_a, '[]'::jsonb);
    ok := false;
  exception when sqlstate '22023' then ok := true;
  end;
  tname := 'T08 empty upload rejected (empty_payload)';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    res := public.sync_observing_sessions('not-a-key', payload);
    ok := false;
  exception when sqlstate '22023' then ok := true;
  end;
  tname := 'T09 missing/invalid device key rejected (invalid_device_key)';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  res := public.sync_observing_sessions(upper(key_a), payload);
  tname := 'T10 device key is not case-sensitive';
  ok := res->>'failed_count' = '0';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  -- ------------------------------------------------------------------ phone B and direct access
  res := public.sync_observing_sessions(key_b, jsonb_build_array(jsonb_set(payload -> 0, '{session_title}', '"Hacked"')));
  tname := 'T11 phone B cannot overwrite phone A''s session (not_owner)';
  ok := res #>> '{failed,0,error_code}' = 'not_owner'
        and (select session_title from public.observing_sessions where id = sess1) = 'Winter planets from the park';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    update public.observing_sessions set session_title = 'Hacked' where id = sess1;
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  tname := 'T12 direct UPDATE with the publishable key is refused';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    delete from public.celestial_observations where session_id = sess1;
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  tname := 'T13 direct DELETE with the publishable key is refused';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    insert into public.observing_sessions (id, session_title, session_date, session_time, observing_location, duration_hours)
    values (gen_random_uuid(), 'x', '2026-01-01', '10:00', 'x', 1);
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  tname := 'T14 direct INSERT with the publishable key is refused';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    select count(*) into n from private.session_device_keys;
    ok := false;
  exception when insufficient_privilege then ok := true;
  end;
  tname := 'T15 device key hashes cannot be read with the publishable key';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  -- ------------------------------------------------------------------ reading and search (MAUI app)
  select count(*) into n from public.celestial_observations where session_id in (sess1, sess2);
  tname := 'T16 publishable key (MAUI app) can read all observations';
  ok := n = 6;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  select count(*) into n from public.search_observations('jupiter', 100, 0) r where r.session_id in (sess1, sess2);
  tname := 'T17 search "jupiter" finds both Jupiter observations';
  ok := n = 2;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  select count(*) into n from public.search_observations('JuP', 100, 0) r where r.session_id in (sess1, sess2);
  tname := 'T18 search is case-insensitive and partial ("JuP")';
  ok := n = 2;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  select string_agg(q || '=' || (select count(*) from public.search_observations(q, 100, 0) r
                                  where r.session_id in (sess1, sess2)), ',')
    into txt
    from unnest(array['Saturn', 'moon', 'orion nebula', 'andromeda galaxy', 'pluto']) q;
  tname := 'T19 Saturn / Moon / Orion Nebula / Andromeda Galaxy found, Pluto empty';
  ok := txt = 'Saturn=1,moon=1,orion nebula=1,andromeda galaxy=1,pluto=0';
  report := report || format(E'\n[%s] %s  (%s)', case when ok then 'PASS' else 'FAIL' end, tname, txt);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  select r.observing_location into txt from public.search_observations('saturn', 100, 0) r where r.session_id = sess1;
  tname := 'T20 search rows include the session details';
  ok := txt = 'Greenwich Park, London';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  select count(*), max(total_count) into n, t from public.search_observations('jupiter', 1, 0);
  tname := 'T21 pagination: limit 1 returns 1 row, total_count >= 2';
  ok := n = 1 and t >= 2;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  select count(*) into n from public.search_observations('%', 100, 0);
  tname := 'T22 "%" is searched literally, not as a wildcard';
  ok := n = 0;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  begin
    perform * from public.search_observations('   ', 20, 0);
    ok := false;
  exception when sqlstate '22023' then ok := true;
  end;
  tname := 'T23 empty search text rejected (search_query_required)';
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  -- ------------------------------------------------------------------ admin checks (postgres role)
  execute 'reset role';
  select count(*) into n from private.session_device_keys where session_id in (sess1, sess2);
  select count(*) into t from private.session_device_keys
   where session_id in (sess1, sess2) and device_key_hash = key_a;
  tname := 'T24 only the HASH of the device key is stored';
  ok := n = 2 and t = 0;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  delete from public.observing_sessions where id = sess1;
  select count(*) into n from public.celestial_observations where session_id = sess1;
  select count(*) into t from private.session_device_keys where session_id = sess1;
  tname := 'T25 deleting a session deletes its observations and key (CASCADE)';
  ok := n = 0 and t = 0;
  report := report || format(E'\n[%s] %s', case when ok then 'PASS' else 'FAIL' end, tname);
  if ok then passed := passed + 1; else failed := failed + 1; end if;

  -- Roll everything back and show the report
  raise exception 'BACKEND TESTS: % passed, % failed%', passed, failed, report;
end
$tests$;
