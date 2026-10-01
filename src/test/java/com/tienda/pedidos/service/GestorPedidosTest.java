package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pedidos de prueba que sirven de linea base: se escribieron contra el GestorPedidos original
 * y se dejan sin cambios durante la refactorizacion para comprobar que la salida es la misma.
 * Cada prueba corre en una transaccion que se revierte al final, asi el stock y el historial
 * de pedidos no se contaminan entre casos.
 */
@SpringBootTest
@Transactional
class GestorPedidosTest {

    private static final double DELTA = 0.001;

    // Productos cargados en data.sql
    private static final long PORTATIL = 1L;   // 1.200.000, stock 10
    private static final long MOUSE = 2L;      // 50.000, stock 100
    private static final long MONITOR = 3L;    // 400.000, stock 2

    @Autowired
    private GestorPedidos gestorPedidos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private EmailService emailService;

    // ---------- Rutas de validacion ----------

    @Test
    @DisplayName("Pedido sin items se rechaza")
    void pedidoSinItems() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L, List.of()));

        assertFalse(r.isConfirmado());
        assertEquals("El pedido no contiene items", r.getMotivoRechazo());
    }

    @Test
    @DisplayName("Stock insuficiente: se piden 5 monitores y solo hay 2")
    void stockInsuficiente() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L, List.of(new ItemPedido(MONITOR, 5))));

        assertFalse(r.isConfirmado());
        assertEquals("Stock insuficiente: producto 3", r.getMotivoRechazo());
        assertEquals(2, stockDe(MONITOR));
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Cliente moroso dentro del horario de corte (10:00) se rechaza")
    void morosoDentroDelHorario() {
        LocalTime diezDeLaManana = LocalTime.of(10, 0);
        try (MockedStatic<LocalTime> reloj = Mockito.mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            reloj.when(LocalTime::now).thenReturn(diezDeLaManana);

            ResultadoPedido r = gestorPedidos.procesarPedido(pedido(3L, List.of(new ItemPedido(MOUSE, 1))));

            assertFalse(r.isConfirmado());
            assertEquals("Cliente con deuda pendiente: $150000.0", r.getMotivoRechazo());
        }
    }

    @Test
    @DisplayName("Cliente moroso despues de las 20:00 se acepta excepcionalmente, sin descuento")
    void morosoFueraDelHorario() {
        LocalTime nueveYMediaDeLaNoche = LocalTime.of(21, 30);
        try (MockedStatic<LocalTime> reloj = Mockito.mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            reloj.when(LocalTime::now).thenReturn(nueveYMediaDeLaNoche);

            ResultadoPedido r = gestorPedidos.procesarPedido(pedido(3L, List.of(new ItemPedido(MOUSE, 1))));

            assertTrue(r.isConfirmado());
            // 50.000 + 19% de IVA
            assertEquals(59_500.0, r.getTotal(), DELTA);
        }
    }

    // ---------- Descuentos por tipo de cliente ----------

    @Test
    @DisplayName("VIP con subtotal mayor a 1.000.000 recibe 15%")
    void vipDescuentoAlto() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(1L, List.of(new ItemPedido(PORTATIL, 1))));

        assertTrue(r.isConfirmado());
        assertNotNull(r.getPedidoId());
        // 1.200.000 - 15% = 1.020.000; IVA 193.800
        assertEquals(1_213_800.0, r.getTotal(), DELTA);
        assertEquals(9, stockDe(PORTATIL));
        assertEquals(0.15, descuentoGuardado(r.getPedidoId()), DELTA);
        verify(emailService).enviar(eq("cliente@correo.com"),
            eq("Confirmacion de pedido #" + r.getPedidoId()),
            eq("Estimado cliente,\n\nSu pedido ha sido confirmado.\n"
                + "Subtotal: $1200000.0\n"
                + "Descuento aplicado: 15%\n"
                + "Impuesto: $193800.0\n"
                + "Total: $1213800.0\n"));
    }

    @Test
    @DisplayName("VIP con subtotal entre 500.000 y 1.000.000 recibe 10%")
    void vipDescuentoMedio() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(1L,
            List.of(new ItemPedido(MONITOR, 1), new ItemPedido(MOUSE, 3))));

        assertTrue(r.isConfirmado());
        // 550.000 - 10% = 495.000; IVA 94.050
        assertEquals(589_050.0, r.getTotal(), DELTA);
        assertEquals(1, stockDe(MONITOR));
        assertEquals(97, stockDe(MOUSE));
    }

    @Test
    @DisplayName("VIP con subtotal pequeno recibe 5%")
    void vipDescuentoBajo() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(1L, List.of(new ItemPedido(MOUSE, 2))));

        assertTrue(r.isConfirmado());
        // 100.000 - 5% = 95.000; IVA 18.050
        assertEquals(113_050.0, r.getTotal(), DELTA);
    }

    @Test
    @DisplayName("FRECUENTE con mas de 10 pedidos previos recibe 8%")
    void frecuenteConMuchosPedidos() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(2L, List.of(new ItemPedido(MOUSE, 2))));

        assertTrue(r.isConfirmado());
        // 100.000 - 8% = 92.000; IVA 17.480
        assertEquals(109_480.0, r.getTotal(), DELTA);
    }

    @Test
    @DisplayName("FRECUENTE con 5 pedidos previos recibe 4%")
    void frecuenteConPocosPedidos() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(5L, List.of(new ItemPedido(MOUSE, 2))));

        assertTrue(r.isConfirmado());
        // 100.000 - 4% = 96.000; IVA 18.240
        assertEquals(114_240.0, r.getTotal(), DELTA);
    }

    @Test
    @DisplayName("Cliente ESTANDAR no recibe descuento y el correo no menciona descuento")
    void clienteEstandarSinDescuento() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L, List.of(new ItemPedido(MOUSE, 1))));

        assertTrue(r.isConfirmado());
        assertEquals(59_500.0, r.getTotal(), DELTA);
        verify(emailService).enviar(eq("cliente@correo.com"),
            eq("Confirmacion de pedido #" + r.getPedidoId()),
            eq("Estimado cliente,\n\nSu pedido ha sido confirmado.\n"
                + "Subtotal: $50000.0\n"
                + "Impuesto: $9500.0\n"
                + "Total: $59500.0\n"));
    }

    // ---------- utilidades ----------

    private PedidoRequest pedido(Long clienteId, List<ItemPedido> items) {
        return new PedidoRequest(clienteId, "cliente@correo.com", items);
    }

    private int stockDe(long productoId) {
        return jdbcTemplate.queryForObject(
            "SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, productoId);
    }

    private double descuentoGuardado(Long pedidoId) {
        return jdbcTemplate.queryForObject(
            "SELECT descuento FROM pedidos WHERE id = ?", Double.class, pedidoId);
    }
}
