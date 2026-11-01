-- Which Google Ads account a connected seller account reads (requirement A4). Empty until the seller chooses one.
-- login_customer_id is the account the access comes through, often a manager account above the chosen one: Google needs
-- it with every call. Ids are digits only, as the API wants them (1234567890, shown to people as 123-456-7890).
ALTER TABLE google_ads_connections
    ADD COLUMN customer_id       text,
    ADD COLUMN login_customer_id text,
    ADD COLUMN account_name      text, -- as last seen on Google, for showing; Google allows an account without a name
    ADD CONSTRAINT google_ads_connections_chosen_together
        CHECK ((customer_id IS NULL) = (login_customer_id IS NULL));
