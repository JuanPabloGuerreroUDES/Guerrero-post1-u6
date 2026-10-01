package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Mismo sistema pero con la campana Black Friday encendida. Como es otro contexto de Spring
// usa su propia base H2 para no chocar con las tablas que ya creo el contexto principal.
@SpringBootTest(properties = {
    "promo.black-friday.activa=true",
    "spring.datasource.url=jdbc:h2:mem:tienda-black-friday;MODE=LEGACY;DB_CLOSE_DELAY=-1"
})
@Transactional
class BlackFridayTest {

    private static final double DELTA = 0.001;
    private static final long PORTATIL = 1L;
    private static final long MOUSE = 2L;
    private static final long MONITOR = 3L;

    @Autowired
    private GestorPedidos gestorPedidos;

    @Test
    @DisplayName("Cliente ESTANDAR recibe 25% con Black Friday activo")
    void estandarRecibeBlackFriday() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L, List.of(new ItemPedido(MOUSE, 1))));

        assertTrue(r.isConfirmado());
        // 50.000 - 25% = 37.500; IVA 7.125
        assertEquals(44_625.0, r.getTotal(), DELTA);
    }

    @Test
    @DisplayName("VIP grande: el 25% de Black Friday le gana al 15% VIP")
    void blackFridayLeGanaAlVip() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(1L, List.of(new ItemPedido(PORTATIL, 1))));

        assertTrue(r.isConfirmado());
        // 1.200.000 - 25% = 900.000; IVA 171.000
        assertEquals(1_071_000.0, r.getTotal(), DELTA);
    }

    @Test
    @DisplayName("Black Friday no salta las validaciones: sin stock se sigue rechazando")
    void blackFridayNoEvitaElRechazo() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L, List.of(new ItemPedido(MONITOR, 5))));

        assertFalse(r.isConfirmado());
        assertEquals("Stock insuficiente: producto 3", r.getMotivoRechazo());
    }

    private PedidoRequest pedido(Long clienteId, List<ItemPedido> items) {
        return new PedidoRequest(clienteId, "cliente@correo.com", items);
    }
}
