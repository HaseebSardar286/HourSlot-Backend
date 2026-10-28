-- Database backstop against double-booking: one staff member cannot hold two active
-- booking items whose time ranges overlap. The application also serialises booking writes
-- per staff with pg_advisory_xact_lock; this constraint catches any path that bypasses it.

-- Denormalised "is this item still occupying the slot" flag, kept in sync with the parent booking.
ALTER TABLE booking_items ADD COLUMN IF NOT EXISTS blocks_slot BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE booking_items i
SET blocks_slot = (UPPER(b.status) IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS') AND b.deleted_at IS NULL)
FROM bookings b
WHERE b.id = i.booking_id;

CREATE OR REPLACE FUNCTION booking_items_set_blocks_slot() RETURNS trigger AS $$
BEGIN
    SELECT (UPPER(b.status) IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS') AND b.deleted_at IS NULL)
    INTO NEW.blocks_slot
    FROM bookings b
    WHERE b.id = NEW.booking_id;
    NEW.blocks_slot := COALESCE(NEW.blocks_slot, TRUE);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_booking_items_blocks_slot ON booking_items;
CREATE TRIGGER trg_booking_items_blocks_slot
    BEFORE INSERT OR UPDATE OF booking_id, start_time, end_time, staff_id ON booking_items
    FOR EACH ROW EXECUTE FUNCTION booking_items_set_blocks_slot();

CREATE OR REPLACE FUNCTION bookings_sync_blocks_slot() RETURNS trigger AS $$
BEGIN
    UPDATE booking_items
    SET blocks_slot = (UPPER(NEW.status) IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS') AND NEW.deleted_at IS NULL)
    WHERE booking_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bookings_sync_blocks_slot ON bookings;
CREATE TRIGGER trg_bookings_sync_blocks_slot
    AFTER UPDATE OF status, deleted_at ON bookings
    FOR EACH ROW EXECUTE FUNCTION bookings_sync_blocks_slot();

-- The exclusion constraint needs btree_gist. If the extension is unavailable, or historical data
-- already overlaps, skip it with a notice instead of failing startup; the advisory lock still applies.
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS btree_gist;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'btree_gist unavailable (%); booking overlap constraint skipped', SQLERRM;
        RETURN;
    END;

    IF EXISTS (
        SELECT 1
        FROM booking_items a
        JOIN booking_items b
          ON a.staff_id = b.staff_id
         AND a.id < b.id
         AND a.blocks_slot AND b.blocks_slot
         AND tstzrange(a.start_time, a.end_time) && tstzrange(b.start_time, b.end_time)
    ) THEN
        RAISE NOTICE 'Existing overlapping bookings found; booking overlap constraint skipped. Resolve them and add ex_booking_items_staff_overlap manually.';
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_booking_items_staff_overlap') THEN
        ALTER TABLE booking_items
            ADD CONSTRAINT ex_booking_items_staff_overlap
            EXCLUDE USING gist (staff_id WITH =, tstzrange(start_time, end_time) WITH &&)
            WHERE (blocks_slot AND staff_id IS NOT NULL);
    END IF;
END $$;
