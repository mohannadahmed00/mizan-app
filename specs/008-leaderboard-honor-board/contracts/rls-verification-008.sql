-- Mizan — spec 008, SC-006 / SC-007 / SC-012 verification.
--
-- Extends spec 007's rls-verification.sql rather than replacing it. Section 1 re-runs 007's
-- assertions unchanged, because this increment is precisely the one most likely to regress them:
-- the whole feature is about making one account's figures visible to another, and the failure mode
-- is a participant's recording history leaking to a stranger.
--
-- Run:  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f rls-verification-008.sql
-- Expect: every assertion passes and the script ends with 'RLS OK 008'. Any failure aborts.
-- The script rolls back; it leaves no rows behind.

begin;

insert into auth.users (id, email) values
    ('11111111-1111-1111-1111-111111111111', 'user-a@example.test'),
    ('22222222-2222-2222-2222-222222222222', 'user-b@example.test'),
    ('33333333-3333-3333-3333-333333333333', 'user-c@example.test');

insert into public.regions (id, display_name, zone, is_fallback) values
    ('r-east', 'Eastern',  'Asia/Riyadh',  false),
    ('r-west', 'Western',  'Europe/London', false),
    ('r-back', 'Elsewhere','UTC',           true);

insert into public.region_zone_map (zone, region_id) values
    ('Asia/Riyadh',   'r-east'),
    ('Europe/London', 'r-west');

-- A and B are in the same region; C is in another.
insert into public.leaderboard_participation (user_id, opted_in, reported_zone) values
    ('11111111-1111-1111-1111-111111111111', true, 'Asia/Riyadh'),
    ('22222222-2222-2222-2222-222222222222', true, 'Asia/Riyadh'),
    ('33333333-3333-3333-3333-333333333333', true, 'Europe/London');

insert into public.leaderboard_entries
    (period_kind, period_start, region_id, user_id, display_name, points, days_engaged, position)
values
    ('DAILY', date '2026-08-16', 'r-east', '11111111-1111-1111-1111-111111111111', 'A', 40, 5, 1),
    ('DAILY', date '2026-08-16', 'r-east', '22222222-2222-2222-2222-222222222222', 'B', 20, 3, 2),
    ('DAILY', date '2026-08-16', 'r-west', '33333333-3333-3333-3333-333333333333', 'C', 60, 6, 1);

insert into public.honor_board_closed
    (period_kind, period_start, region_id, user_id, display_name)
values
    ('WEEKLY', date '2026-08-08', 'r-east', '11111111-1111-1111-1111-111111111111', 'A');

set local role authenticated;

-- ---------------------------------------------------------------------------
-- 1. Spec 007's guarantees still hold. Raw records remain unreadable across
--    accounts. THIS INCREMENT MUST NOT HAVE WIDENED THEM.
-- ---------------------------------------------------------------------------

set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';

insert into public.day_records (user_id, date, catalogue_version)
values ('11111111-1111-1111-1111-111111111111', date '2026-08-16', 1);

insert into public.completions (id, user_id, credited_date, task_slug, points_awarded, recorded_at)
values ('aaaaaaaa-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        date '2026-08-16', 'fajr-jamaah', 2, now());

set local request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';

do $$
declare visible integer;
begin
    select count(*) into visible from public.completions
     where user_id = '11111111-1111-1111-1111-111111111111';
    if visible <> 0 then
        raise exception 'SC-006 FAILED: 008 widened completions — B reads % of A''s completions', visible;
    end if;

    select count(*) into visible from public.day_records
     where user_id = '11111111-1111-1111-1111-111111111111';
    if visible <> 0 then
        raise exception 'SC-006 FAILED: 008 widened day_records — B reads % of A''s day records', visible;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. A participant reads the aggregate for their OWN region only (SC-007).
-- ---------------------------------------------------------------------------

do $$
declare visible integer;
begin
    -- B is in r-east and must see both r-east rows, including A's.
    select count(*) into visible from public.leaderboard_entries where region_id = 'r-east';
    if visible <> 2 then
        raise exception 'FR-009 FAILED: B sees % of its own region''s 2 entries', visible;
    end if;

    -- and must see nothing at all from r-west.
    select count(*) into visible from public.leaderboard_entries where region_id = 'r-west';
    if visible <> 0 then
        raise exception 'SC-007 FAILED: B reads % entries from another region', visible;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. No client may write the aggregate — not even its own row (SC-006).
-- ---------------------------------------------------------------------------

do $$
declare affected integer;
begin
    begin
        insert into public.leaderboard_entries
            (period_kind, period_start, region_id, user_id, display_name, points, days_engaged, position)
        values ('DAILY', date '2026-08-16', 'r-east',
                '22222222-2222-2222-2222-222222222222', 'B', 999999, 99, 1);
        raise exception 'SC-006 FAILED: a client inserted a leaderboard entry';
    exception when insufficient_privilege then
        null;  -- expected
    end;

    update public.leaderboard_entries set points = 999999
     where user_id = '22222222-2222-2222-2222-222222222222';
    get diagnostics affected = row_count;
    if affected <> 0 then
        raise exception 'SC-006 FAILED: a client inflated % of its own entries', affected;
    end if;

    delete from public.leaderboard_entries
     where user_id = '11111111-1111-1111-1111-111111111111';
    get diagnostics affected = row_count;
    if affected <> 0 then
        raise exception 'SC-006 FAILED: a client deleted % of a rival''s entries', affected;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. A client cannot choose its region (FR-014), and cannot read the mapping
--    it would need in order to choose one.
-- ---------------------------------------------------------------------------

do $$
declare visible integer; assigned text;
begin
    select count(*) into visible from public.region_zone_map;
    if visible <> 0 then
        raise exception 'FR-014 FAILED: a client reads % rows of the zone→region map', visible;
    end if;

    -- B attempts to move itself into r-west by writing region_id directly.
    update public.leaderboard_participation
       set region_id = 'r-west'
     where user_id = '22222222-2222-2222-2222-222222222222';

    select region_id into assigned from public.leaderboard_participation
     where user_id = '22222222-2222-2222-2222-222222222222';
    if assigned <> 'r-east' then
        raise exception 'FR-014 FAILED: a client moved itself into region %', assigned;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. An unmatched zone lands in the fallback region rather than nowhere (FR-015).
-- ---------------------------------------------------------------------------

do $$
declare assigned text;
begin
    update public.leaderboard_participation
       set reported_zone = 'Antarctica/Troll'
     where user_id = '22222222-2222-2222-2222-222222222222';

    select region_id into assigned from public.leaderboard_participation
     where user_id = '22222222-2222-2222-2222-222222222222';
    if assigned <> 'r-back' then
        raise exception 'FR-015 FAILED: an unmatched zone landed in region %, expected the fallback', assigned;
    end if;

    -- put B back for the remaining assertions
    update public.leaderboard_participation
       set reported_zone = 'Asia/Riyadh'
     where user_id = '22222222-2222-2222-2222-222222222222';
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Opting out clears every OPEN period (FR-004) and leaves every CLOSED
--    period exactly as it stands — rankings and Honor Board alike (FR-004a).
--
--    Both halves are asserted together because the boundary between them is the
--    whole decision. A closed period admits no mutation of any kind.
-- ---------------------------------------------------------------------------

set local role postgres;

-- A holds rows in one OPEN period and one CLOSED period.
insert into public.leaderboard_periods (period_kind, period_start, region_id, state) values
    ('DAILY',  date '2026-08-16', 'r-east', 'OPEN'),
    ('WEEKLY', date '2026-08-08', 'r-east', 'CLOSED');

insert into public.leaderboard_entries
    (period_kind, period_start, region_id, user_id, display_name, points, days_engaged, position)
values
    ('WEEKLY', date '2026-08-08', 'r-east', '11111111-1111-1111-1111-111111111111', 'A', 120, 6, 1);

set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';

update public.leaderboard_participation set opted_in = false
 where user_id = '11111111-1111-1111-1111-111111111111';

set local role postgres;   -- inspect the true state, past RLS

do $$
declare remaining integer;
begin
    -- the OPEN period is cleared
    select count(*) into remaining from public.leaderboard_entries
     where user_id = '11111111-1111-1111-1111-111111111111'
       and period_kind = 'DAILY' and period_start = date '2026-08-16';
    if remaining <> 0 then
        raise exception 'FR-004 FAILED: % open-period ranking rows survived an opt-out', remaining;
    end if;

    -- the CLOSED period is untouched
    select count(*) into remaining from public.leaderboard_entries
     where user_id = '11111111-1111-1111-1111-111111111111'
       and period_kind = 'WEEKLY' and period_start = date '2026-08-08';
    if remaining <> 1 then
        raise exception 'FR-004a FAILED: an opt-out rewrote a CLOSED period''s standings';
    end if;

    -- and so is its Honor Board membership
    select count(*) into remaining from public.honor_board_closed
     where user_id = '11111111-1111-1111-1111-111111111111';
    if remaining <> 1 then
        raise exception 'FR-004a FAILED: an opt-out destroyed closed Honor Board recognition';
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. The Honor Board exposes nothing about non-qualifiers (FR-030, SC-012).
--    Asserted structurally: no column a client can read carries a threshold,
--    a shortfall, or a non-qualifier count.
-- ---------------------------------------------------------------------------

do $$
declare leaky text;
begin
    select string_agg(column_name, ', ') into leaky
      from information_schema.columns
     where table_schema = 'public'
       and table_name   = 'honor_board_closed'
       and column_name ~* 'threshold|shortfall|short|remaining|missed|failed|non_?qualif|behind';
    if leaky is not null then
        raise exception 'SC-012 FAILED: honor_board_closed exposes non-qualifier data: %', leaky;
    end if;

    select string_agg(column_name, ', ') into leaky
      from information_schema.columns
     where table_schema = 'public'
       and table_name   = 'leaderboard_entries'
       and column_name ~* 'threshold|shortfall|behind|trend|last_position|is_last|is_bottom';
    if leaky is not null then
        raise exception 'FR-038/FR-039 FAILED: leaderboard_entries exposes rank-shaming data: %', leaky;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. The Honor Board threshold is not retrievable by any client (FR-030,
--    SC-012). Knowing it turns "I am not on the board" into "I was N days
--    short", which is the deficit framing Principle IX forbids.
-- ---------------------------------------------------------------------------

set local role authenticated;
set local request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';

do $$
declare visible integer;
begin
    select count(*) into visible from public.honor_board_config;
    if visible <> 0 then
        raise exception 'SC-012 FAILED: a client reads % rows of the Honor Board threshold config', visible;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 9. Withdrawal actually reaches something (FR-004).
--
--    This guards the failure mode that looks like success: leaderboard_periods
--    is the join target of the withdrawal delete, so if nothing ever populates
--    it, opting out matches zero rows and silently does nothing. Section 6
--    above seeds its own period rows and would pass regardless — this asserts
--    the real table is populated for the current period.
-- ---------------------------------------------------------------------------

set local role postgres;

do $$
declare open_periods integer;
begin
    select count(*) into open_periods
      from public.leaderboard_periods
     where state = 'OPEN';
    if open_periods = 0 then
        raise exception
            'FR-004 FAILED: no OPEN period rows exist, so the withdrawal delete can never match — opt-out would silently do nothing. Has recompute_open_periods() run?';
    end if;
end;
$$;

select 'RLS OK 008' as result;

rollback;
