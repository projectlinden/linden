-- Removes personal data from log lines before they are stored (docs/operations/log-inventory.md).
-- Called by Fluent Bit for every record; record.service is set from the container label.
--
-- Return codes for Fluent Bit: -1 = drop the record, 0 = keep unchanged, 1 = keep the modified record.

-- Shaped like name@domain.tld – "Thread@1a2b" (no dot after the @) is left alone
local EMAIL = "[%w%._%%%+%-]+@[%w%-]+%.[%w%.%-]*%a"
local IPV4 = "%d+%.%d+%.%d+%.%d+"

-- Keycloak event fields that identify a person or a login session
local KEYCLOAK_FIELDS = { "username", "ipAddress", "code_id" }

local function redact_keycloak(line)
    for _, field in ipairs(KEYCLOAK_FIELDS) do
        -- e.g. ', username="anna@example.com"' → ''
        line = line:gsub(",?%s*" .. field .. '="[^"]*"', "")
    end
    -- IP addresses can also appear outside the event fields
    return (line:gsub(IPV4, "[ip removed]"))
end

local function is_postgres_statement(line)
    -- STATEMENT: the full SQL text; DETAIL: e.g. "Key (email)=(anna@example.com) already exists."
    return line:find("STATEMENT:", 1, true) ~= nil or line:find("DETAIL:", 1, true) ~= nil
end

function redact(tag, timestamp, record)
    local line = record["log"]
    if line == nil then
        return 0, timestamp, record
    end

    local service = record["service"]
    if service == "postgres" and is_postgres_statement(line) then
        return -1, timestamp, record
    end

    local original = line
    if service == "keycloak" then
        line = redact_keycloak(line)
    end

    -- Last line of defence for every service: no email address is ever stored
    line = line:gsub(EMAIL, "[email removed]")

    if line == original then
        return 0, timestamp, record
    end
    record["log"] = line
    return 1, timestamp, record
end
