package com.tienda.pedidos.descuento;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

// 12% cuando el pedido tiene mas de 20 unidades sumando todos los items
@Component
public class DescuentoVolumen implements CampanaDescuento {

    private static final int UNIDADES_MINIMAS = 20;

    @Override
    public double calcular(ContextoPedido contexto) {
        int totalUnidades = contexto.getRequest().getItems().stream()
            .mapToInt(ItemPedido::getCantidad)
            .sum();
        return totalUnidades > UNIDADES_MINIMAS ? 0.12 : 0.0;
    }
}
