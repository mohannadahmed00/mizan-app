-- Operator-owned regional calendar bands. Clients report a zone and never choose a region.
insert into public.regions (id, display_name, zone, is_fallback)
values
    ('arabia-riyadh', 'Arabia (Riyadh)', 'Asia/Riyadh', false),
    ('egypt-cairo', 'Egypt (Cairo)', 'Africa/Cairo', false),
    ('pakistan-karachi', 'Pakistan (Karachi)', 'Asia/Karachi', false),
    ('hawaii-honolulu', 'Hawaii (Honolulu)', 'Pacific/Honolulu', false),
    ('fallback-utc', 'UTC', 'UTC', true)
on conflict (id) do update set
    display_name = excluded.display_name,
    zone = excluded.zone,
    is_fallback = excluded.is_fallback;

insert into public.region_zone_map (zone, region_id)
values
    ('Asia/Riyadh', 'arabia-riyadh'),
    ('Africa/Cairo', 'egypt-cairo'),
    ('Asia/Karachi', 'pakistan-karachi'),
    ('Pacific/Honolulu', 'hawaii-honolulu'),
    ('UTC', 'fallback-utc')
on conflict (zone) do update set
    region_id = excluded.region_id;
