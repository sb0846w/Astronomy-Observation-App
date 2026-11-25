-- =====================================================================
-- Row Level Security
--
-- Access model (agreed for the coursework):
--   * Android app: signs in with Supabase *anonymous sign-in* (no login screen).
--     Each installation gets its own auth user id, and RLS checks it on every write.
--   * MAUI app: reads with the publishable key only (role "anon"), no sign-in needed.
--   * Everybody can READ sessions and observations (shared observation discovery).
--   * Only the owner can INSERT / UPDATE / DELETE their own rows.
-- `(select auth.uid())` is wrapped in a sub-select so Postgres evaluates it once per
-- query instead of once per row (Supabase performance recommendation).
-- =====================================================================

alter table public.profiles               enable row level security;
alter table public.observing_sessions     enable row level security;
alter table public.celestial_observations enable row level security;

-- Table privileges: RLS filters rows, GRANTs decide which commands are possible at all.
revoke all on public.profiles, public.observing_sessions, public.celestial_observations from anon, authenticated;
grant select                         on public.observing_sessions, public.celestial_observations to anon, authenticated;
grant insert, update, delete         on public.observing_sessions, public.celestial_observations to authenticated;
grant select, update (display_name)  on public.profiles to authenticated;

-- ---------------------------------------------------------------- profiles (private to the owner)

create policy "Users can read their own profile"
  on public.profiles for select to authenticated
  using ((select auth.uid()) = id);

create policy "Users can update their own profile"
  on public.profiles for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);
-- No insert/delete policies: rows are created by the auth trigger and removed by ON DELETE CASCADE.

-- ---------------------------------------------------------------- observing_sessions

create policy "Anyone can read observing sessions"
  on public.observing_sessions for select to anon, authenticated
  using (true);

create policy "Users can create their own sessions"
  on public.observing_sessions for insert to authenticated
  with check ((select auth.uid()) = user_id);

create policy "Users can update their own sessions"
  on public.observing_sessions for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "Users can delete their own sessions"
  on public.observing_sessions for delete to authenticated
  using ((select auth.uid()) = user_id);

-- ---------------------------------------------------------------- celestial_observations

create policy "Anyone can read celestial observations"
  on public.celestial_observations for select to anon, authenticated
  using (true);

-- Ownership is validated through the parent session as well as the row itself.
create policy "Users can add observations to their own sessions"
  on public.celestial_observations for insert to authenticated
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1 from public.observing_sessions s
      where s.id = session_id and s.user_id = (select auth.uid())
    )
  );

create policy "Users can update observations in their own sessions"
  on public.celestial_observations for update to authenticated
  using ((select auth.uid()) = user_id)
  with check (
    (select auth.uid()) = user_id
    and exists (
      select 1 from public.observing_sessions s
      where s.id = session_id and s.user_id = (select auth.uid())
    )
  );

create policy "Users can delete their own observations"
  on public.celestial_observations for delete to authenticated
  using ((select auth.uid()) = user_id);
