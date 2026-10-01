package com.tienda.pedidos.validacion;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

// Eslabon 1: el pedido debe traer items y debe haber stock para cada uno
@Component
public class ValidadorStock extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorStock.class);

    private final ProductoRepository productoRepository;

    public ValidadorStock(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        List<ItemPedido> items = contexto.getRequest().getItems();
        if (items == null || items.isEmpty()) {
            log.warn("Pedido rechazado: sin items. Cliente {}", contexto.getRequest().getClienteId());
            contexto.rechazar("El pedido no contiene items");
            return;
        }
        for (ItemPedido item : items) {
            if (productoRepository.obtenerStock(item.getProductoId()) < item.getCantidad()) {
                log.warn("Stock insuficiente para producto {}", item.getProductoId());
                contexto.rechazar("Stock insuficiente: producto " + item.getProductoId());
                return;
            }
        }
    }
}
