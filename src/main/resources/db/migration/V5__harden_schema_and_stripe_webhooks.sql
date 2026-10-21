-- Harden constraints/indexes and add Stripe webhook persistence.
-- All statements are safe on the existing database where legacy rows may have NULL audit flags.

UPDATE public.users SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.tweets SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.comments SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.likes SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.bookmarks SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.follower SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.notifications SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.statistics SET is_deleted = FALSE WHERE is_deleted IS NULL;
UPDATE public.subscriptions SET is_deleted = FALSE WHERE is_deleted IS NULL;

UPDATE public.statistics
SET views = COALESCE(views, 0),
    likes = COALESCE(likes, 0),
    comments = COALESCE(comments, 0),
    bookmarks = COALESCE(bookmarks, 0),
    retweets = COALESCE(retweets, 0),
    quotes = COALESCE(quotes, 0);

-- The legacy Hibernate-generated check did not include MENTION, so mention notifications failed.
ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('FOLLOW', 'LIKE', 'COMMENT', 'MENTION'));

-- A user must not be able to follow the same account twice. Remove accidental duplicates first.
DELETE FROM public.follower f
USING public.follower duplicate
WHERE f.id > duplicate.id
  AND f.follower_id = duplicate.follower_id
  AND f.following_id = duplicate.following_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_follower_pair
    ON public.follower (follower_id, following_id);

-- Email uniqueness cannot be enforced blindly on legacy data because the development database
-- already contains duplicate/blank emails. Application-level checks reject new duplicates.
-- These expression indexes make the duplicate checks efficient.
CREATE INDEX IF NOT EXISTS idx_users_email_lower
    ON public.users (LOWER(email));
CREATE INDEX IF NOT EXISTS idx_users_user_name_lower
    ON public.users (LOWER(user_name));

CREATE INDEX IF NOT EXISTS idx_tweets_user_created
    ON public.tweets (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_tweet_created
    ON public.comments (tweet_id, created_at);
CREATE INDEX IF NOT EXISTS idx_likes_tweet ON public.likes (tweet_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_user_created
    ON public.bookmarks (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_follower_following ON public.follower (following_id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON public.notifications (recipient_id, created_at DESC);

ALTER TABLE public.subscriptions
    ADD COLUMN IF NOT EXISTS last_stripe_event_created_at timestamp(6) without time zone;

CREATE TABLE IF NOT EXISTS public.stripe_webhook_events (
    event_id varchar(255) PRIMARY KEY,
    event_type varchar(255) NOT NULL,
    event_created_at timestamp(6) without time zone,
    processed_at timestamp(6) without time zone NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PROCESSED'
);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_created
    ON public.stripe_webhook_events (event_created_at);
