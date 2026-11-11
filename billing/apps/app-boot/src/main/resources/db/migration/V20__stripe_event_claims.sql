-- A retry worker claims an event before it reads from Stripe what applying it needs, so two workers never prepare
-- the same event in the same tick. The claim is a deadline, not a held row lock: the attempt runs outside the
-- transaction that takes it, and once the deadline passes another worker may take the event over. A worker that
-- dies mid-attempt therefore costs at most this long instead of holding the event forever. The claim is separate
-- from next_attempt_at, so taking it never moves the retry schedule.
alter table stripe_events add column claimed_until timestamptz;
