-- Mizan — spec 008, remote schema for Leaderboards & Honor Board.
--
-- This file is the contract. `supabase/migrations/0002_leaderboard_honor_board.sql` must be
-- byte-identical to it, and the merge gate checks that with `diff`, exactly as spec 007 does.
--
-- The governing rule of this increment: NO POLICY ON AN EXISTING TABLE IS WIDENED. `completions`
-- and `day_records` keep their `_select_own` policies from 0001. A participant reads other
-- participants only through `leaderboard_entries`, which holds nothing but what FR-002 says opting
-- in publishes. See research R1.

-- ---------------------------------------------------------------------------
-- 1. Regions — administrator-defined. Readable by any signed-in client so a
--    ranking can be labelled (FR-016). Writable by nobody (FR-017).
-- ---------------------------------------------------------------------------

create table public.regions (
    id            text primary key,
    display_name  text        not null,
    zone          text        not null,   -- IANA zone id; fixes period boundaries (FR-010)
    is_fallback   boolean     not null default false,
    created_at    timestamptz not null default now()
);

-- Exactly one fallback region must exist (FR-015).
create unique index regions_single_fallback on public.regions (is_fallback) where is_fallback;

alter table public.regions enable row level security;

create policy regions_select_all on public.regions
    for select to authenticated using (true);

-- No insert, update or delete policy. Regions are seeded by an operator (FR-017).

-- ---------------------------------------------------------------------------
-- 2. Zone → region mapping. Readable by NO client. A participant reports a zone
--    and is told its region; it never sees the mapping, so it cannot reverse it
--    to find a favourable zone to claim (FR-014, research R3).
-- ---------------------------------------------------------------------------

create table public.region_zone_map (
    zone       text primary key,
    region_id  text not null references public.regions (id)
);

alter table public.region_zone_map enable row level security;

-- Deliberately no policy of any kind: RLS with no policy denies every client.

-- ---------------------------------------------------------------------------
-- 3. Participation consent. Off by default for every account, including
--    accounts that existed before this increment (FR-001).
-- ---------------------------------------------------------------------------

create table public.leaderboard_participation (
    user_id        uuid primary key references auth.users (id),
    opted_in       boolean     not null default false,
    region_id      text        references public.regions (id),
    reported_zone  text,
    updated_at     timestamptz not null default now()
);

alter table public.leaderboard_participation enable row level security;

create policy participation_select_own on public.leaderboard_participation
    for select to authenticated using (user_id = auth.uid());

create policy participation_insert_own on public.leaderboard_participation
    for insert to authenticated with check (user_id = auth.uid());

create policy participation_update_own on public.leaderboard_participation
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- No delete policy: consent is revoked by setting opted_in = false, which triggers
-- withdrawal below. A row is never removed, so the reported zone survives for
-- re-evaluation if the participant opts back in.

-- A client may report its zone but MUST NOT choose its region. This trigger
-- overwrites whatever region_id the client sent with the mapped one (FR-014).
create or replace function public.assign_region()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.region_id := coalesce(
        (select region_id from public.region_zone_map where zone = new.reported_zone),
        (select id from public.regions where is_fallback)   -- FR-015
    );
    new.updated_at := now();
    return new;
end;
$$;

create trigger participation_assign_region
    before insert or update on public.leaderboard_participation
    for each row execute function public.assign_region();

-- ---------------------------------------------------------------------------
-- 3a. Period lifecycle. Exists so that "a closed period never changes" is a
--     JOIN CONDITION rather than a convention: both mutating paths — the
--     aggregation job and the withdrawal delete — are scoped to periods whose
--     state is not CLOSED, so neither can reach one even by mistake.
--     (FR-025, FR-031, FR-004a, research R5.)
-- ---------------------------------------------------------------------------

create table public.leaderboard_periods (
    period_kind   text not null check (period_kind in ('DAILY', 'WEEKLY', 'MONTHLY')),
    period_start  date not null,
    region_id     text not null references public.regions (id),
    state         text not null default 'OPEN'
                       check (state in ('OPEN', 'CLOSED')),  -- no settlement window (FR-025)
    closed_at     timestamptz,
    primary key (period_kind, period_start, region_id)
);

create index leaderboard_periods_open
    on public.leaderboard_periods (state) where state <> 'CLOSED';

alter table public.leaderboard_periods enable row level security;

-- Readable so a ranking can say whether it is final. Writable by no client.
create policy leaderboard_periods_select_all on public.leaderboard_periods
    for select to authenticated using (true);

-- ---------------------------------------------------------------------------
-- 3b. Honor Board configuration. The days-engaged threshold, administrator-
--     defined and never user-configurable (FR-028).
--
--     Readable by NO client. FR-030 and SC-012 forbid a participant retrieving
--     the threshold at all: knowing it turns "I am not on the board" into
--     "I was N days short", which is exactly the deficit framing Principle IX
--     rules out. RLS with no policy denies every client, the same device used
--     for region_zone_map.
--
--     Keyed by period_kind because weekly and monthly need different bars, and
--     DAILY is absent because there is no daily Honor Board (FR-027a).
-- ---------------------------------------------------------------------------

create table public.honor_board_config (
    period_kind     text primary key check (period_kind in ('WEEKLY', 'MONTHLY')),
    threshold_days  integer not null check (threshold_days > 0),
    updated_at      timestamptz not null default now()
);

alter table public.honor_board_config enable row level security;

-- Deliberately no policy of any kind. Seeded by an operator (FR-028).

-- ---------------------------------------------------------------------------
-- 4. The aggregate. THE ONLY TABLE ANY PARTICIPANT READS ABOUT ANYONE ELSE.
--    Holds only what FR-002 says opting in publishes.
-- ---------------------------------------------------------------------------

create table public.leaderboard_entries (
    period_kind   text        not null check (period_kind in ('DAILY', 'WEEKLY', 'MONTHLY')),
    period_start  date        not null,
    region_id     text        not null references public.regions (id),
    user_id       uuid        not null references auth.users (id),
    display_name  text        not null,
    points        integer     not null,
    days_engaged  integer     not null,
    position      integer     not null,
    computed_at   timestamptz not null default now(),
    primary key (period_kind, period_start, region_id, user_id)
);

create index leaderboard_entries_page
    on public.leaderboard_entries (period_kind, period_start, region_id, position);

alter table public.leaderboard_entries enable row level security;

-- Select is permitted only within the reader's own region (SC-007).
create policy leaderboard_entries_select_own_region on public.leaderboard_entries
    for select to authenticated
    using (
        region_id = (
            select region_id from public.leaderboard_participation
             where user_id = auth.uid() and opted_in
        )
    );

-- NO insert, update or delete policy for any client, including for its own row.
-- Written solely by the scheduled job under elevated privilege. This is what makes
-- SC-006 structural rather than procedural — there is no client write path to defend.

-- ---------------------------------------------------------------------------
-- 5. Honor Board for CLOSED periods. Written once at freeze, then never altered.
--    Like closed rankings, it is outside every mutating path (FR-004a, FR-031).
--    WEEKLY and MONTHLY only — a days-engaged threshold over a single day can
--    only be 0 or 1, so there is no daily Honor Board (FR-027a).
-- ---------------------------------------------------------------------------

create table public.honor_board_closed (
    period_kind   text not null check (period_kind in ('WEEKLY', 'MONTHLY')),  -- FR-027a
    period_start  date not null,
    region_id     text not null references public.regions (id),
    user_id       uuid not null references auth.users (id),
    display_name  text not null,
    closed_at     timestamptz not null default now(),
    primary key (period_kind, period_start, region_id, user_id)
);

alter table public.honor_board_closed enable row level security;

create policy honor_board_select_own_region on public.honor_board_closed
    for select to authenticated
    using (
        region_id = (
            select region_id from public.leaderboard_participation
             where user_id = auth.uid() and opted_in
        )
    );

-- No insert, update or DELETE policy for any client, and the withdrawal function
-- below is scoped to open periods, so it cannot reach this table either. FR-031.

-- ---------------------------------------------------------------------------
-- 6. Withdrawal. Fires when a participant clears consent.
--
--    Removes the participant from every period still OPEN (FR-004).
--    Leaves every CLOSED period exactly as it stands — rankings and Honor Board
--    alike (FR-004a). The scope is a join on leaderboard_periods rather than a
--    conditional, so a closed period is not merely skipped: it is not selected.
--
--    FR-002a requires the opt-in copy to disclose this before anyone joins.
--    A participant cannot erase past standings, and consent they would not have
--    given had they understood that is not consent (research R7).
-- ---------------------------------------------------------------------------

create or replace function public.withdraw_participant()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if old.opted_in and not new.opted_in then
        delete from public.leaderboard_entries e
              using public.leaderboard_periods p
              where e.user_id      = new.user_id
                and p.period_kind  = e.period_kind
                and p.period_start = e.period_start
                and p.region_id    = e.region_id
                and p.state       <> 'CLOSED';
        -- honor_board_closed holds only CLOSED periods and is therefore
        -- unreachable from here by construction (FR-004a).
    end if;
    return new;
end;
$$;

create trigger participation_withdraw
    after update on public.leaderboard_participation
    for each row execute function public.withdraw_participant();

-- ---------------------------------------------------------------------------
-- 7. Aggregation. Recomputes OPEN periods only; a closed period is not in the
--    working set, so it cannot be re-scored (FR-025, FR-031, research R2/R5).
--
--    Sums the points the DEVICE froze at write time and groups by the
--    credited_date the DEVICE computed. The server never re-derives a date and
--    never recomputes a points figure — Principle III, Principle VII.
-- ---------------------------------------------------------------------------

create or replace function public.recompute_open_periods()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    -- STEP 0 — MATERIALISE THE CURRENT PERIODS FIRST. Nothing else creates
    -- rows in leaderboard_periods, and both mutating paths join it: the fold
    -- below, and the withdrawal delete above. If this table is empty the
    -- withdrawal delete matches zero rows and OPT-OUT SILENTLY DOES NOTHING,
    -- which is the worst failure this feature can produce — it looks like it
    -- worked. So, for every region and each of DAILY, WEEKLY and MONTHLY:
    --
    --   insert into public.leaderboard_periods (period_kind, period_start, region_id)
    --   select ... the period containing now() in that region's zone ...
    --   on conflict (period_kind, period_start, region_id) do nothing;
    --
    -- WEEKLY must start on a Saturday, matching WeekBoundary (FR-011).
    -- `do nothing` keeps this idempotent and, critically, cannot resurrect or
    -- reopen a period already marked CLOSED (Rule B).
    --
    -- Then the working set is
    --   select * from public.leaderboard_periods where state <> 'CLOSED'
    -- so a closed period is never selected, not merely skipped.
    --
    -- For each region and each open period, fold non-reversed completions for
    -- opted-in participants into leaderboard_entries, keyed by
    -- (period_kind, period_start, region_id, user_id):
    --
    --   points       = sum(points_awarded)              where reversed_at is null
    --   days_engaged = count(distinct credited_date)    where reversed_at is null
    --   position     = rank over (points desc, max(recorded_at) asc)
    --
    -- The tie-break is "who reached the total earliest" (FR-022). recorded_at is
    -- device-reported, so a forged clock can reorder a tie — FR-022a bounds that
    -- to exactly that and no more: it cannot change any total, days_engaged or
    -- region, and cannot lift anyone above a higher total.
    --
    -- A participant who opts in mid-period is scored over the WHOLE period
    -- (FR-021a), so the fold does not filter completions by consent date.
    --
    -- then, for any period whose boundary has passed in its region's timezone,
    -- insert qualifying members (days_engaged >= threshold) into
    -- honor_board_closed — WEEKLY and MONTHLY only (FR-027a) — and set that
    -- period's state to 'CLOSED', which removes it from this working set and
    -- from the withdrawal delete's scope permanently.
    --
    -- The freeze is immediate: there is no settlement window (FR-025). A
    -- completion that arrives after the boundary does not enter the closed
    -- period, and still counts in full in the participant's own records
    -- (FR-025a).
    --
    -- A participant who is currently opted out is not entered into any newly
    -- opening period (FR-004b): the fold joins leaderboard_participation on
    -- opted_in.
    --
    -- The threshold comes from public.honor_board_config, joined on period_kind
    -- — never a constant in this function (FR-028).
    null;
end;
$$;

-- Scheduled by the operator. Not invocable by any client: no execute grant to
-- `authenticated` is issued for this function.
revoke all on function public.recompute_open_periods() from public, authenticated, anon;
