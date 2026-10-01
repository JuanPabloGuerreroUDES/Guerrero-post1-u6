package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

/**
 * Campanas Corporativo y Volumen (Black Friday apagado, como en application.properties).
 * Se escribieron cuando las campanas estaban como eslabones de la cadena y se mantienen
 * iguales despues de moverlas a EstrategiaDescuento.
 */
@SpringBootTest
@Transactional
class CampanasDescuentoTest {

    private static final double DELTA = 0.001;
    private static final long PORTATIL = 1L;   // 1.200.000
    private static final long MOUSE = 2L;      // 50.000
    private static final long CABLE = 4L;      // 5.000, stock 200

    @Autowired
    private GestorPedidos gestorPedidos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private EmailService emailService;

    @Test
    @DisplayName("Cliente ESTANDAR con NIT recibe 10% corporativo")
    void corporativoConNit() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(6L, List.of(new ItemPedido(MOUSE, 1))));

        assertTrue(r.isConfirmado());
        // 50.000 - 10% = 45.000; IVA 8.550
        assertEquals(53_550.0, r.getTotal(), DELTA);
        assertEquals(0.10, descuentoGuardado(r.getPedidoId()), DELTA);
        verify(emailService).enviar(anyString(), anyString(), contains("Descuento aplicado: 10%"));
    }

    @Test
    @DisplayName("Mas de 20 unidades da 12% por volumen")
    void volumenMasDe20Unidades() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L, List.of(new ItemPedido(CABLE, 25))));

        assertTrue(r.isConfirmado());
        // 125.000 - 12% = 110.000; IVA 20.900
        assertEquals(130_900.0, r.getTotal(), DELTA);
        assertEquals(0.12, descuentoGuardado(r.getPedidoId()), DELTA);
    }

    @Test
    @DisplayName("Exactamente 20 unidades no alcanza el descuento por volumen")
    void volumenJusto20Unidades() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(4L,
            List.of(new ItemPedido(CABLE, 15), new ItemPedido(MOUSE, 5))));

        assertTrue(r.isConfirmado());
        // 75.000 + 250.000 = 325.000 sin descuento; IVA 61.750
        assertEquals(386_750.0, r.getTotal(), DELTA);
    }

    @Test
    @DisplayName("VIP pequeno (5%) con 25 unidades: gana volumen (12%)")
    void vipConVolumenGanaLaCampana() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(1L, List.of(new ItemPedido(CABLE, 25))));

        assertTrue(r.isConfirmado());
        assertEquals(130_900.0, r.getTotal(), DELTA);
        assertEquals(0.12, descuentoGuardado(r.getPedidoId()), DELTA);
    }

    @Test
    @DisplayName("VIP grande (15%) con 21 unidades: gana el descuento VIP")
    void vipGrandeGanaAlVolumen() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(1L,
            List.of(new ItemPedido(PORTATIL, 1), new ItemPedido(CABLE, 21))));

        assertTrue(r.isConfirmado());
        // 1.305.000 - 15% = 1.109.250; IVA 210.757,5
        assertEquals(1_320_007.5, r.getTotal(), DELTA);
        assertEquals(0.15, descuentoGuardado(r.getPedidoId()), DELTA);
    }

    @Test
    @DisplayName("Corporativo (10%) con 25 unidades: gana volumen (12%)")
    void corporativoConVolumen() {
        ResultadoPedido r = gestorPedidos.procesarPedido(pedido(6L, List.of(new ItemPedido(CABLE, 25))));

        assertTrue(r.isConfirmado());
        assertEquals(130_900.0, r.getTotal(), DELTA);
    }

    private PedidoRequest pedido(Long clienteId, List<ItemPedido> items) {
        return new PedidoRequest(clienteId, "cliente@correo.com", items);
    }

    private double descuentoGuardado(Long pedidoId) {
        return jdbcTemplate.queryForObject(
            "SELECT descuento FROM pedidos WHERE id = ?", Double.class, pedidoId);
    }
}
