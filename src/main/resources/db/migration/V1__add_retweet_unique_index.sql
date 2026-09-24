CREATE UNIQUE INDEX ux_tweet_retweet_user
    ON public.tweets (parent_id, user_id)
    WHERE content IS NULL;