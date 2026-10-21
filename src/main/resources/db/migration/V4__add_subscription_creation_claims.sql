CREATE TABLE public.subscription_creation_claims (
    user_id BIGINT PRIMARY KEY,
    claim_token VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_subscription_creation_claims_user
        FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_subscription_creation_claims_expires_at
    ON public.subscription_creation_claims(expires_at);
