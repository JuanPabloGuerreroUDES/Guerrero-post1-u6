package com.tienda.pedidos.descuento;

import org.springframework.stereotype.Component;

import java.util.Map;

// Unico punto donde se decide que regla aplica segun el tipo de cliente.
// Reemplaza el if/else anidado que tenia GestorPedidos.
@Component
public class SelectorEstrategiaDescuento {

    private final Map<String, EstrategiaDescuento> estrategias;
    private final EstrategiaDescuento porDefecto;

    public SelectorEstrategiaDescuento(DescuentoVip vip, DescuentoFrecuente frecuente,
                                       DescuentoEstandar estandar) {
        this.estrategias = Map.of("VIP", vip, "FRECUENTE", frecuente, "ESTANDAR", estandar);
        this.porDefecto = estandar;
    }

    public EstrategiaDescuento seleccionar(String tipoCliente) {
        return estrategias.getOrDefault(tipoCliente, porDefecto);
    }
}
