-- A period of a subscription is renewed at most once. The renewal logic already guarantees it (the subscription row
-- is locked, the charge key names the period and the attempt, and the schedule moves forward in the same transaction
-- as the charge); this index makes the same promise at the database, so a bug or a manual change can never record
-- the same period as renewed twice. The failed attempt that follows a violation rolls the charge back with it.

create unique index subscription_events_renewed_once_idx on subscription_events (subscription_id, period_number) where type = 'RENEWED';
