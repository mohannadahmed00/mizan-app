-- Mizan — spec 007, SC-008 verification.
--
-- FR-023 requires that no account can read or modify another account's records, enforced
-- "by the account service itself and not by the client", and SC-008 requires that this be
-- verified "directly against the service rather than through the app". A test that runs
-- through RemoteDataSource proves only that the client asked politely — so this runs in
-- SQL, against the project, with two real users.
--
-- Run:  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f rls-verification.sql
-- Expect: every assertion below passes and the script ends with 'RLS OK'. Any failure
-- aborts. The script rolls back; it leaves no rows behind.

begin;

-- Two users, created directly so no email delivery is involved.
insert into auth.users (id, email) values
    ('11111111-1111-1111-1111-111111111111', 'user-a@example.test'),
    ('22222222-2222-2222-2222-222222222222', 'user-b@example.test');

-- Each user records one day and one completion, as themselves.
set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';

insert into public.day_records (user_id, date, catalogue_version)
values ('11111111-1111-1111-1111-111111111111', date '2026-08-16', 1);

insert into public.completions (id, user_id, credited_date, task_slug, points_awarded, recorded_at)
values ('aaaaaaaa-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        date '2026-08-16', 'fajr-jamaah', 2, now());

set local request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';

insert into public.day_records (user_id, date, catalogue_version)
values ('22222222-2222-2222-2222-222222222222', date '2026-08-16', 1);

insert into public.completions (id, user_id, credited_date, task_slug, points_awarded, recorded_at)
values ('bbbbbbbb-0000-0000-0000-000000000001',
        '22222222-2222-2222-2222-222222222222',
        date '2026-08-16', 'fajr-jamaah', 2, now());

-- ---------------------------------------------------------------------------
-- 1. B cannot read A's records.
-- ---------------------------------------------------------------------------

do $$
declare visible integer;
begin
    select count(*) into visible from public.completions
     where user_id = '11111111-1111-1111-1111-111111111111';
    if visible <> 0 then
        raise exception 'SC-008 FAILED: user B can read % of user A''s completions', visible;
    end if;

    select count(*) into visible from public.day_records
     where user_id = '11111111-1111-1111-1111-111111111111';
    if visible <> 0 then
        raise exception 'SC-008 FAILED: user B can read % of user A''s day records', visible;
    end if;

    -- and B sees exactly its own
    select count(*) into visible from public.completions;
    if visible <> 1 then
        raise exception 'SC-008 FAILED: user B sees % completions, expected only its own 1', visible;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. B cannot modify A's records (zero rows affected, not an error).
-- ---------------------------------------------------------------------------

do $$
declare affected integer;
begin
    update public.completions set reversed_at = now()
     where id = 'aaaaaaaa-0000-0000-0000-000000000001';
    get diagnostics affected = row_count;
    if affected <> 0 then
        raise exception 'SC-008 FAILED: user B reversed % of user A''s completions', affected;
    end if;

    update public.day_records set catalogue_version = 99
     where user_id = '11111111-1111-1111-1111-111111111111';
    get diagnostics affected = row_count;
    if affected <> 0 then
        raise exception 'SC-008 FAILED: user B rewrote % of user A''s day records', affected;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. B cannot insert a record attributed to A (WITH CHECK must reject it).
-- ---------------------------------------------------------------------------

do $$
begin
    begin
        insert into public.completions (id, user_id, credited_date, task_slug, points_awarded, recorded_at)
        values ('cccccccc-0000-0000-0000-000000000001',
                '11111111-1111-1111-1111-111111111111',
                date '2026-08-16', 'fajr-jamaah', 2, now());
        raise exception 'SC-008 FAILED: user B inserted a completion owned by user A';
    exception when insufficient_privilege then
        null;  -- expected
    end;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. Nobody can delete a user record — there is no delete policy (FR-007d, FR-018).
-- ---------------------------------------------------------------------------

do $$
declare affected integer;
begin
    delete from public.completions where id = 'bbbbbbbb-0000-0000-0000-000000000001';
    get diagnostics affected = row_count;
    if affected <> 0 then
        raise exception 'FR-018 FAILED: a delete policy exists on completions (% rows removed)', affected;
    end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. The catalogue is readable by any signed-in device and writable by none (R10).
-- ---------------------------------------------------------------------------

do $$
begin
    begin
        insert into public.catalogue_publications (version, effective_from, format_version, payload)
        values (999, current_date, 1, '{}'::jsonb);
        raise exception 'FR-027 FAILED: an authenticated client can publish a catalogue version';
    exception when insufficient_privilege then
        null;  -- expected
    end;
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. The merges are server-side and monotone (FR-017, FR-019, R5).
-- ---------------------------------------------------------------------------

do $$
declare v integer; r timestamptz;
begin
    -- a newer catalogue cannot claim an already-recorded day
    insert into public.day_records (user_id, date, catalogue_version)
    values ('22222222-2222-2222-2222-222222222222', date '2026-08-16', 7)
    on conflict (user_id, date) do update set catalogue_version = excluded.catalogue_version;

    select catalogue_version into v from public.day_records
     where user_id = '22222222-2222-2222-2222-222222222222' and date = date '2026-08-16';
    if v <> 1 then
        raise exception 'R5 FAILED: day record moved to catalogue version %, expected 1', v;
    end if;

    -- a tombstone, once set, cannot be cleared by a later write
    update public.completions set reversed_at = now()
     where id = 'bbbbbbbb-0000-0000-0000-000000000001';

    insert into public.completions (id, user_id, credited_date, task_slug, points_awarded, recorded_at, reversed_at)
    values ('bbbbbbbb-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222',
            date '2026-08-16', 'fajr-jamaah', 2, now(), null)
    on conflict (id) do update set reversed_at = excluded.reversed_at;

    select reversed_at into r from public.completions
     where id = 'bbbbbbbb-0000-0000-0000-000000000001';
    if r is null then
        raise exception 'FR-018 FAILED: a tombstone was cleared by a later write';
    end if;
end;
$$;

select 'RLS OK' as result;

rollback;
