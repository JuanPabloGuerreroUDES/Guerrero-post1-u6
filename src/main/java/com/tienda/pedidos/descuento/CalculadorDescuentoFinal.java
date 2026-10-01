package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

import java.util.List;

// Unico lugar donde se decide el descuento final del pedido: se compara el descuento por tipo
// de cliente con el de cada campana y gana el mayor (la misma regla que habia antes).
@Component
public class CalculadorDescuentoFinal {

    private final SelectorEstrategiaDescuento selectorPorCliente;
    private final List<CampanaDescuento> campanas;

    public CalculadorDescuentoFinal(SelectorEstrategiaDescuento selectorPorCliente,
                                    List<CampanaDescuento> campanas) {
        this.selectorPorCliente = selectorPorCliente;
        this.campanas = campanas;
    }

    public double calcular(ContextoPedido contexto) {
        double porTipoCliente = selectorPorCliente.seleccionar(contexto.getTipoCliente()).calcular(contexto);
        double porCampana = campanas.stream()
            .mapToDouble(campana -> campana.calcular(contexto))
            .max()
            .orElse(0.0);
        return Math.max(porTipoCliente, porCampana);
    }
}
