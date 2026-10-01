package com.tienda.pedidos.descuento;

import com.tienda.pedidos.repository.ClienteRepository;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

// FRECUENTE: el porcentaje depende de cuantos pedidos ha hecho antes el cliente
@Component
public class DescuentoFrecuente implements EstrategiaDescuento {

    private final ClienteRepository clienteRepository;

    public DescuentoFrecuente(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public double calcular(ContextoPedido contexto) {
        int pedidosPrevios = clienteRepository.contarPedidos(contexto.getRequest().getClienteId());
        if (pedidosPrevios > 10) return 0.08;
        if (pedidosPrevios > 3) return 0.04;
        return 0.0;
    }
}
