INSERT INTO productos (id, nombre, precio) VALUES
    (1, 'Portatil', 1200000),
    (2, 'Mouse inalambrico', 50000),
    (3, 'Monitor 27"', 400000),
    (4, 'Cable USB-C', 5000);

INSERT INTO inventario (producto_id, stock) VALUES
    (1, 10),
    (2, 100),
    (3, 2),
    (4, 200);

INSERT INTO clientes (id, nombre, email, tipo_cliente) VALUES
    (1, 'Laura Rincon',   'laura.rincon@correo.com',   'VIP'),
    (2, 'Andres Pabon',   'andres.pabon@correo.com',   'FRECUENTE'),
    (3, 'Carlos Duarte',  'carlos.duarte@correo.com',  'MOROSO'),
    (4, 'Sofia Mantilla', 'sofia.mantilla@correo.com', 'ESTANDAR'),
    (5, 'Diego Serrano',  'diego.serrano@correo.com',  'FRECUENTE');

-- Carlos tiene dos facturas sin pagar (150.000 en total) y una pagada
INSERT INTO facturas (cliente_id, monto, pagada) VALUES
    (3, 100000, false),
    (3, 50000, false),
    (3, 999, true);

-- Historial: Andres lleva 12 pedidos (mas de 10) y Diego 5 (entre 4 y 10)
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
SELECT 2, 50000, 0, 9500, 59500, TIMESTAMP '2026-08-01 10:00:00', 'CONFIRMADO'
FROM SYSTEM_RANGE(1, 12);

INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
SELECT 5, 50000, 0, 9500, 59500, TIMESTAMP '2026-08-01 10:00:00', 'CONFIRMADO'
FROM SYSTEM_RANGE(1, 5);

-- Cliente corporativo: es ESTANDAR pero tiene NIT registrado
INSERT INTO clientes (id, nombre, email, tipo_cliente, nit) VALUES
    (6, 'Distribuidora Andina SAS', 'compras@andina.com', 'ESTANDAR', '900123456-7');
