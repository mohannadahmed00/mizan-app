-- Mizan — spec 007, remote schema contract.
--
-- This file is the contract. The applied copy lives at
-- supabase/migrations/0001_identity_cloud_sync.sql and must stay identical.
--
-- Three properties this schema enforces mechanically, rather than by client discipline:
--   1. No account can read or modify another account's records (FR-023, SC-008) — RLS, not the app.
--   2. Nothing deletes a user record — there is no DELETE policy anywhere below (FR-007d, FR-018).
--   3. Both merges are monotone, server-side (FR-017, FR-019, R5):
--        day_records.catalogue_version can only decrease  -> LEAST()
--        completions.reversed_at can only be set, never cleared -> COALESCE()

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------

create table public.profiles (
    id           uuid primary key references auth.users (id) on delete cascade,
    display_name text,                                     -- optional, empty by default (FR-007e)
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy profiles_select_own on public.profiles
    for select using (id = auth.uid());

create policy profiles_insert_own on public.profiles
    for insert with check (id = auth.uid());

create policy profiles_update_own on public.profiles
    for update using (id = auth.uid()) with check (id = auth.uid());

-- No delete policy. Account deletion is out of scope for this phase and is recorded
-- in the spec as a follow-up obligation, not a silent omission.

-- ---------------------------------------------------------------------------
-- day_records  — the version pointer for a date, not the plan (R4)
-- ---------------------------------------------------------------------------

create table public.day_records (
    user_id           uuid        not null references auth.users (id) on delete cascade,
    date              date        not null,
    catalogue_version integer     not null check (catalogue_version > 0),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    primary key (user_id, date)
);

create index day_records_user_updated on public.day_records (user_id, updated_at);

alter table public.day_records enable row level security;

create policy day_records_select_own on public.day_records
    for select using (user_id = auth.uid());

create policy day_records_insert_own on public.day_records
    for insert with check (user_id = auth.uid());

create policy day_records_update_own on public.day_records
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- The merge, server-side and monotone. A newer catalogue can never claim an
-- already-recorded day, whatever order the two devices arrive in (R5).
--
-- Client upsert:
--   POST /rest/v1/day_records?on_conflict=user_id,date
--   Prefer: resolution=merge-duplicates,return=representation
create or replace function public.day_records_merge()
    returns trigger
    language plpgsql
    -- Empty search_path: a trigger function that resolves unqualified names through
    -- a caller-controlled search_path is a privilege-escalation vector, and Supabase's
    -- own security advisor flags its absence. Everything used below is in pg_catalog.
    set search_path = ''
as $$
begin
    new.catalogue_version := least(old.catalogue_version, new.catalogue_version);
    new.created_at        := old.created_at;
    new.updated_at        := now();
    return new;
end;
$$;

create trigger day_records_merge_before_update
    before update on public.day_records
    for each row execute function public.day_records_merge();

-- ---------------------------------------------------------------------------
-- completions — one recorded occurrence, keyed by the client-generated UUID
-- ---------------------------------------------------------------------------

create table public.completions (
    id             uuid        primary key,            -- client-generated (Principle V)
    user_id        uuid        not null references auth.users (id) on delete cascade,
    credited_date  date        not null,
    task_slug      text        not null,
    points_awarded integer     not null check (points_awarded > 0),
    recorded_at    timestamptz not null,
    reversed_at    timestamptz,                        -- the undo tombstone (FR-018)
    updated_at     timestamptz not null default now()
);

create index completions_user_date    on public.completions (user_id, credited_date);
create index completions_user_updated on public.completions (user_id, updated_at);

alter table public.completions enable row level security;

create policy completions_select_own on public.completions
    for select using (user_id = auth.uid());

create policy completions_insert_own on public.completions
    for insert with check (user_id = auth.uid());

create policy completions_update_own on public.completions
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- No delete policy. Undo writes a tombstone; nothing removes the row.

-- Monotone tombstone, and every historical figure is write-once. points_awarded,
-- recorded_at, credited_date and task_slug are frozen at first write: a merge, a
-- retry, or a second device cannot re-score a recorded completion (Principle III).
--
-- Client upsert:
--   POST /rest/v1/completions?on_conflict=id
--   Prefer: resolution=merge-duplicates,return=representation
create or replace function public.completions_merge()
    returns trigger
    language plpgsql
    -- Empty search_path: a trigger function that resolves unqualified names through
    -- a caller-controlled search_path is a privilege-escalation vector, and Supabase's
    -- own security advisor flags its absence. Everything used below is in pg_catalog.
    set search_path = ''
as $$
begin
    new.credited_date  := old.credited_date;
    new.task_slug      := old.task_slug;
    new.points_awarded := old.points_awarded;
    new.recorded_at    := old.recorded_at;
    new.reversed_at    := coalesce(old.reversed_at, new.reversed_at);
    new.updated_at     := now();
    return new;
end;
$$;

create trigger completions_merge_before_update
    before update on public.completions
    for each row execute function public.completions_merge();

-- ---------------------------------------------------------------------------
-- catalogue_publications — administrator content, insert-only from outside the API
-- ---------------------------------------------------------------------------

create table public.catalogue_publications (
    version        integer     primary key,
    effective_from date        not null,
    format_version integer     not null,   -- an app that does not know this value skips the row (FR-028)
    payload        jsonb       not null,   -- same shape as domain/src/main/resources/catalogue/valid-catalogue.json
    published_at   timestamptz not null default now()
);

alter table public.catalogue_publications enable row level security;

-- Readable by any signed-in device; writable by nobody through the API. Publishing is an
-- operator action against the database, and no admin surface ships in this phase
-- (Principle VI, FR-027).
create policy catalogue_publications_select_all on public.catalogue_publications
    for select to authenticated using (true);

-- Deliberately no insert, update, or delete policy. A published version is immutable:
-- rewriting one would re-score every day recorded against it (Principle III, R10).

-- ---------------------------------------------------------------------------
-- Publication rule (operator discipline, stated here because the client relies on it)
-- ---------------------------------------------------------------------------
--
-- A new version's effective_from SHOULD be a future date at the moment of publication.
-- The client is correct without it — min-version merging converges either way — but
-- honouring it means two devices never disagree about which version a date belongs to
-- in the first place.
