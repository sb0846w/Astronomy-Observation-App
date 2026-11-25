-- =====================================================================
-- AstroLog DEMO DATA: 5 observing sessions, 18 celestial observations
--
-- Run in the Supabase dashboard → SQL Editor (as the default postgres user).
--
-- * Idempotent: uses FIXED UUIDs and INSERT … ON CONFLICT (id) DO UPDATE, so running it
--   again restores the demo rows to these values and never creates duplicates.
-- * Clearly marked: every demo id starts with "de000000-", every session title ends with
--   "(Demo)" and the observer is "AstroLog Demo".
-- * Never touches real data: it only writes rows with the demo ids above.
-- * Demo sessions have no device key, so no phone can change them through the app.
-- * Remove everything again with demo_remove.sql.
-- =====================================================================

begin;

insert into public.observing_sessions (
  id, session_title, session_date, session_time, observing_location, duration_hours,
  description, sky_conditions, observer_name, equipment, bortle_scale, seeing_rating,
  latitude, longitude, synced_at)
values
  ('de000000-0000-4000-a000-000000000001', 'Night Sky Observation – Jaipur (Demo)', '2026-11-18', '19:30',
   'Nahargarh Fort, Jaipur, Rajasthan', 2.5,
   'Demo data. Evening planet session from the fort walls, city lights to the south.',
   'Clear', 'AstroLog Demo', 'Telescope', 6, 4, 26.9373, 75.8155, now()),

  ('de000000-0000-4000-a000-000000000002', 'Planet Evening – Greenwich Park (Demo)', '2026-11-12', '18:15',
   'Greenwich Park, London, UK', 2,
   'Demo data. Quick session after work next to the Royal Observatory.',
   'Mostly clear', 'AstroLog Demo', 'Telescope', 8, 3, 51.4769, -0.0005, now()),

  ('de000000-0000-4000-a000-000000000003', 'Dark Sky Trip – Galloway Forest (Demo)', '2026-11-07', '22:00',
   'Galloway Forest Dark Sky Park, Scotland', 3.5,
   'Demo data. Very dark site, Milky Way clearly visible overhead.',
   'Clear', 'AstroLog Demo', 'Binoculars', 2, 4, 55.0939, -4.4987, now()),

  ('de000000-0000-4000-a000-000000000004', 'Winter Nebulae – Mount Abu (Demo)', '2026-10-30', '23:30',
   'Guru Shikhar, Mount Abu, Rajasthan', 3,
   'Demo data. High-altitude site, cold but steady air after midnight.',
   'Clear, slight haze near the horizon', 'AstroLog Demo', 'Telescope', 3, 4, 24.6511, 72.7794, now()),

  ('de000000-0000-4000-a000-000000000005', 'Morning Planets – Pune (Demo)', '2026-10-24', '05:00',
   'Sinhagad Fort, Pune, Maharashtra', 1.5,
   'Demo data. Pre-dawn session before sunrise.',
   'Partly cloudy', 'AstroLog Demo', 'Naked eye', 5, 2, 18.3663, 73.7559, now())
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
  longitude          = excluded.longitude;

insert into public.celestial_observations (
  id, session_id, celestial_object, object_type, location_in_sky, observation_details,
  additional_comments, observed_time, has_photo, sort_order)
values
  -- Session 1: Jaipur
  ('de000000-0000-4000-b000-000000000101', 'de000000-0000-4000-a000-000000000001', 'Jupiter', 'Planet',
   'Eastern sky, in Taurus', 'Jupiter observed with four visible Galilean moons using a telescope. Two dark cloud belts clear at 100x.',
   'Clear sky with good visibility.', '19:45', false, 0),
  ('de000000-0000-4000-b000-000000000102', 'de000000-0000-4000-a000-000000000001', 'Saturn', 'Planet',
   'South-western sky, in Aquarius', 'Rings clearly separated from the globe, Titan visible as a faint star to the east.',
   'Rings appear quite narrow this year.', '20:10', false, 1),
  ('de000000-0000-4000-b000-000000000103', 'de000000-0000-4000-a000-000000000001', 'Moon', 'Moon',
   'South-east, about 30° high', 'Waxing gibbous Moon, craters sharp along the terminator, Copernicus easy to see.',
   null, '20:40', true, 2),
  ('de000000-0000-4000-b000-000000000104', 'de000000-0000-4000-a000-000000000001', 'Pleiades (M45)', 'Star cluster',
   'Eastern sky, in Taurus', 'Six stars with the naked eye, more than 30 in the finder scope.',
   'Best at low magnification.', '21:15', false, 3),

  -- Session 2: Greenwich
  ('de000000-0000-4000-b000-000000000201', 'de000000-0000-4000-a000-000000000002', 'Venus', 'Planet',
   'South-west, low after sunset', 'Very bright, showing a clear gibbous phase in the telescope.',
   'Some shimmering because it was low.', '18:20', false, 0),
  ('de000000-0000-4000-b000-000000000202', 'de000000-0000-4000-a000-000000000002', 'Saturn', 'Planet',
   'South, in Aquarius', 'Rings and the Cassini Division glimpsed in moments of steady air.',
   null, '18:50', false, 1),
  ('de000000-0000-4000-b000-000000000203', 'de000000-0000-4000-a000-000000000002', 'Moon', 'Moon',
   'South-east, low', 'Thin crescent Moon with earthshine on the dark part.',
   'Beautiful naked-eye view.', '19:05', true, 2),
  ('de000000-0000-4000-b000-000000000204', 'de000000-0000-4000-a000-000000000002', 'Mars', 'Planet',
   'Eastern sky, in Leo', 'Small orange disc, no surface detail visible through the city haze.',
   'Try again when it is higher.', '19:40', false, 3),

  -- Session 3: Galloway
  ('de000000-0000-4000-b000-000000000301', 'de000000-0000-4000-a000-000000000003', 'Andromeda Galaxy (M31)', 'Galaxy',
   'High in the south, in Andromeda', 'Long oval glow with a bright core; M32 visible as a fuzzy star nearby.',
   'Easy with averted vision.', '22:20', false, 0),
  ('de000000-0000-4000-b000-000000000302', 'de000000-0000-4000-a000-000000000003', 'Orion Nebula (M42)', 'Nebula',
   'South-east, in Orion''s sword', 'Bright fuzzy cloud around the Trapezium stars, wings of nebulosity visible.',
   null, '23:05', false, 1),
  ('de000000-0000-4000-b000-000000000303', 'de000000-0000-4000-a000-000000000003', 'Pleiades (M45)', 'Star cluster',
   'High in the east, in Taurus', 'Counted seven stars naked eye, dozens in binoculars, faint blue haze around them.',
   'Dark sky makes a big difference.', '23:20', false, 2),
  ('de000000-0000-4000-b000-000000000304', 'de000000-0000-4000-a000-000000000003', 'Jupiter', 'Planet',
   'East, in Taurus', 'Small bright disc with three moons in a line in 10x50 binoculars.',
   null, '23:45', false, 3),

  -- Session 4: Mount Abu
  ('de000000-0000-4000-b000-000000000401', 'de000000-0000-4000-a000-000000000004', 'Orion Nebula (M42)', 'Nebula',
   'South, in Orion''s sword', 'Greenish glow with lots of structure, all four Trapezium stars split at 150x.',
   'Steady air after midnight.', '00:15', true, 0),
  ('de000000-0000-4000-b000-000000000402', 'de000000-0000-4000-a000-000000000004', 'Mars', 'Planet',
   'East, in Cancer', 'Orange disc, a darker marking visible near the centre at moments.',
   null, '01:30', false, 1),
  ('de000000-0000-4000-b000-000000000403', 'de000000-0000-4000-a000-000000000004', 'Andromeda Galaxy (M31)', 'Galaxy',
   'North-west, getting low', 'Core visible, outer parts lost in the haze near the horizon.',
   'Observe earlier in the evening next time.', '01:50', false, 2),

  -- Session 5: Pune
  ('de000000-0000-4000-b000-000000000501', 'de000000-0000-4000-a000-000000000005', 'Venus', 'Planet',
   'East, before sunrise', 'Brilliant "morning star", easy to see even as the sky brightened.',
   null, '05:10', false, 0),
  ('de000000-0000-4000-b000-000000000502', 'de000000-0000-4000-a000-000000000005', 'Jupiter', 'Planet',
   'West, setting', 'Bright steady point, much brighter than any star nearby.',
   'Clouds came in after 05:30.', '05:15', false, 1),
  ('de000000-0000-4000-b000-000000000503', 'de000000-0000-4000-a000-000000000005', 'Moon', 'Moon',
   'West, low', 'Waning gibbous Moon, maria easy to make out with the naked eye.',
   null, '05:25', false, 2)
on conflict (id) do update set
  session_id          = excluded.session_id,
  celestial_object    = excluded.celestial_object,
  object_type         = excluded.object_type,
  location_in_sky     = excluded.location_in_sky,
  observation_details = excluded.observation_details,
  additional_comments = excluded.additional_comments,
  observed_time       = excluded.observed_time,
  has_photo           = excluded.has_photo,
  sort_order          = excluded.sort_order;

commit;

-- Check: should show 5 sessions and 18 observations
select (select count(*) from public.observing_sessions     where id::text like 'de000000-%') as demo_sessions,
       (select count(*) from public.celestial_observations where id::text like 'de000000-%') as demo_observations;
