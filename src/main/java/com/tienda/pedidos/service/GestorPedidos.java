package com.tienda.pedidos.service;

import com.tienda.pedidos.descuento.SelectorEstrategiaDescuento;
import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.repository.PedidoRepository;
import com.tienda.pedidos.repository.ProductoRepository;
import com.tienda.pedidos.validacion.ContextoPedido;
import com.tienda.pedidos.validacion.ValidadorCliente;
import com.tienda.pedidos.validacion.ValidadorPedido;
import com.tienda.pedidos.validacion.ValidadorStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Orquesta el proceso del pedido: validar, calcular, guardar y notificar.
// Los detalles de cada paso viven en su propia clase.
@Service
public class GestorPedidos {

    private static final Logger log = LoggerFactory.getLogger(GestorPedidos.class);
    private static final double IVA = 0.19;

    private final ValidadorPedido primerValidador;
    private final SelectorEstrategiaDescuento selectorDescuento;
    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final NotificacionPedidoService notificacion;

    public GestorPedidos(ValidadorStock validadorStock, ValidadorCliente validadorCliente,
                         SelectorEstrategiaDescuento selectorDescuento,
                         ProductoRepository productoRepository, PedidoRepository pedidoRepository,
                         NotificacionPedidoService notificacion) {
        validadorStock.encadenar(validadorCliente);
        this.primerValidador = validadorStock;
        this.selectorDescuento = selectorDescuento;
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.notificacion = notificacion;
    }

    public ResultadoPedido procesarPedido(PedidoRequest request) {
        log.info("Iniciando procesamiento de pedido para cliente {}", request.getClienteId());

        ContextoPedido contexto = new ContextoPedido(request);
        primerValidador.validar(contexto);
        if (contexto.isRechazado()) {
            return ResultadoPedido.rechazado(contexto.getMotivoRechazo());
        }

        double subtotal = calcularSubtotal(request);
        contexto.setSubtotal(subtotal);

        double descuento = selectorDescuento.seleccionar(contexto.getTipoCliente()).calcular(contexto);
        double impuesto = (subtotal - subtotal * descuento) * IVA;
        double total = subtotal - (subtotal * descuento) + impuesto;

        Long pedidoId = pedidoRepository.guardar(contexto, descuento, impuesto, total);
        notificacion.notificarConfirmacion(contexto, pedidoId, descuento, impuesto, total);

        log.info("Pedido {} confirmado. Total: {}", pedidoId, total);
        return ResultadoPedido.confirmado(pedidoId, total);
    }

    private double calcularSubtotal(PedidoRequest request) {
        double subtotal = 0;
        for (ItemPedido item : request.getItems()) {
            subtotal += productoRepository.obtenerPrecio(item.getProductoId()) * item.getCantidad();
        }
        return subtotal;
    }
}
