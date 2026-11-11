-- Historical H2 data import only. The application never loads this file.
CREATE TABLE IF NOT EXISTS payment_receipts (
    order_id VARCHAR(64) PRIMARY KEY,
    payment_key VARCHAR(200) NOT NULL UNIQUE,
    payment_mode VARCHAR(4) NOT NULL CHECK(payment_mode IN ('TEST','LIVE')),
    total_amount INT NOT NULL CHECK(total_amount > 0),
    balance_amount INT NOT NULL,
    status VARCHAR(24) NOT NULL CHECK(status IN ('DONE','PARTIAL_CANCELED','CANCELED','ABORTED','EXPIRED')),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (order_id) REFERENCES trade_orders(order_id),
    CHECK (balance_amount >= 0 AND balance_amount <= total_amount)
);
CREATE TABLE IF NOT EXISTS payment_events (
    snapshot_id CHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    balance_amount INT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (order_id) REFERENCES payment_receipts(order_id)
);

CREATE TABLE IF NOT EXISTS toss_customer_keys (
    buyer VARCHAR(50) PRIMARY KEY,
    customer_key VARCHAR(36) NOT NULL UNIQUE,
    FOREIGN KEY (buyer) REFERENCES student(unisno)
);
CREATE TABLE IF NOT EXISTS toss_escrow_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    escrow_id VARCHAR(200) UNIQUE,
    product_id VARCHAR(200) UNIQUE,
    status VARCHAR(25) NOT NULL,
    delivery_id VARCHAR(200) UNIQUE,
    received_at TIMESTAMP(6) NULL,
    receiver_name VARCHAR(50),
    verified_at TIMESTAMP(6),
    FOREIGN KEY (order_id) REFERENCES trade_orders(order_id)
);

-- One virtual seller ledger entry per verified test payment. Amounts are KRW.
CREATE TABLE IF NOT EXISTS escrow_simulations (
    order_id VARCHAR(64) PRIMARY KEY,
    seller VARCHAR(50) NOT NULL,
    amount INT NOT NULL CHECK (amount > 0),
    balance INT NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('HELD','CONFIRMED','SETTLED','PARTIALLY_REFUNDED','REFUNDED')),
    held_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at TIMESTAMP(6),
    settled_at TIMESTAMP(6),
    FOREIGN KEY (order_id) REFERENCES trade_orders(order_id),
    CHECK (balance >= 0 AND balance <= amount)
);

-- Only references and verified test statuses are retained. No bank account data.
CREATE TABLE IF NOT EXISTS toss_test_seller_links (
    member_id BIGINT PRIMARY KEY,
    seller_id VARCHAR(64) NOT NULL UNIQUE,
    ref_seller_id VARCHAR(64) NOT NULL UNIQUE,
    FOREIGN KEY (member_id) REFERENCES student(member_id)
);
CREATE TABLE IF NOT EXISTS toss_test_payout_links (
    order_id VARCHAR(64) PRIMARY KEY,
    member_id BIGINT NOT NULL,
    payout_id VARCHAR(64) NOT NULL UNIQUE,
    amount INT NOT NULL CHECK (amount > 0),
    status VARCHAR(24) NOT NULL,
    verified_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES trade_orders(order_id),
    FOREIGN KEY (member_id) REFERENCES toss_test_seller_links(member_id)
);
