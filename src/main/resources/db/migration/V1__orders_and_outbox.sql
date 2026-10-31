CREATE TABLE orders (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    failure_mode VARCHAR(24) NOT NULL,
    total NUMERIC(15, 2) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE order_items (
    order_id UUID NOT NULL REFERENCES orders(id),
    line_number INTEGER NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (order_id, line_number)
);
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;
