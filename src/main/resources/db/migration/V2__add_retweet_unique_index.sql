DELETE FROM public.tweets
WHERE id IN (
    SELECT id
    FROM public.tweets
    WHERE content IS NULL
      AND id NOT IN (
        SELECT MIN(id)
        FROM public.tweets
        WHERE content IS NULL
        GROUP BY parent_id, user_id
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tweet_retweet_user
    ON public.tweets (parent_id, user_id)
    WHERE content IS NULL;