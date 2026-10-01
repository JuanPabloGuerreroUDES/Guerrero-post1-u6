package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;

// Cada regla de descuento devuelve un porcentaje (0.10 = 10%) a partir de los datos del pedido.
// Ninguna depende de que otra se haya evaluado antes.
public interface EstrategiaDescuento {
    double calcular(ContextoPedido contexto);
}
