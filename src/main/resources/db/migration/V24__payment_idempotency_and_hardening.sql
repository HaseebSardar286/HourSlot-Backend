-- Production hardening: Stripe payment idempotency + unpaid online booking expiry support.

-- Unique Stripe payment intent / session id so webhook retries cannot insert duplicates.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_provider_payment_id
    ON payments (provider_payment_id)
    WHERE provider_payment_id IS NOT NULL AND provider_payment_id <> '';

-- Help expire abandoned online checkouts.
CREATE INDEX IF NOT EXISTS idx_bookings_unpaid_pending_online
    ON bookings (created_at)
    WHERE deleted_at IS NULL
      AND UPPER(status) = 'PENDING'
      AND UPPER(COALESCE(payment_method, '')) = 'ONLINE'
      AND UPPER(COALESCE(payment_status, '')) = 'UNPAID';
