package com.tienda.pedidos.descuento;

import com.tienda.pedidos.repository.ClienteRepository;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

// 10% para clientes que tienen NIT registrado
@Component
public class DescuentoCorporativo implements CampanaDescuento {

    private final ClienteRepository clienteRepository;

    public DescuentoCorporativo(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public double calcular(ContextoPedido contexto) {
        boolean tieneNit = clienteRepository.buscarNit(contexto.getRequest().getClienteId())
            .filter(nit -> !nit.isBlank())
            .isPresent();
        return tieneNit ? 0.10 : 0.0;
    }
}
