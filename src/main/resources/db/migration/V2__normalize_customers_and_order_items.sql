CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    CONSTRAINT customers_contact_unique UNIQUE NULLS NOT DISTINCT (full_name, address, phone)
);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers(id);

UPDATE orders o SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
  AND c.address IS NOT DISTINCT FROM o.customer_address
  AND c.phone IS NOT DISTINCT FROM o.customer_phone;

ALTER TABLE orders ALTER COLUMN customer_id SET NOT NULL;
CREATE INDEX orders_customer_id_idx ON orders(customer_id);

-- V1 не сохраняла идентификатор товара. Сопоставляем по названию и цене.
-- Если товар удалён или цена изменилась, сохраняем старую пару в каталоге.
INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES products(id);

-- Одинаковые строки каталога не позволяют восстановить исходный id:
-- выбираем минимальный, не размножая позиции заказа.
UPDATE order_items i SET product_id = (
    SELECT min(p.id) FROM products p WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
CREATE INDEX order_items_product_id_idx ON order_items(product_id);

ALTER TABLE orders
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;
ALTER TABLE order_items DROP COLUMN product_name, DROP COLUMN product_price;
