-- Server-only qualification configuration. RLS exposes no client read policy for this table.
insert into public.honor_board_config (period_kind, threshold_days)
values
    ('WEEKLY', 5),
    ('MONTHLY', 20)
on conflict (period_kind) do update set
    threshold_days = excluded.threshold_days,
    updated_at = now();
