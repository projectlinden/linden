-- A circle. Its name is written by people, so it is content: stored only as an encrypted payload
-- (first byte = format version, 0 = fake lock). The server never reads it.
CREATE TABLE circle
(
    id         uuid PRIMARY KEY,
    payload    bytea       NOT NULL CHECK (octet_length(payload) BETWEEN 1 AND 4096),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Who is in which circle. The server needs this to check membership on every request (ADR-0002).
CREATE TABLE circle_member
(
    circle_id uuid        NOT NULL REFERENCES circle (id) ON DELETE CASCADE,
    user_id   uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role      text        NOT NULL CHECK (role IN ('owner', 'member')),
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (circle_id, user_id)
);

-- "List my circles" looks up by user; the primary key only helps lookups by circle.
CREATE INDEX circle_member_user_id_idx ON circle_member (user_id);
