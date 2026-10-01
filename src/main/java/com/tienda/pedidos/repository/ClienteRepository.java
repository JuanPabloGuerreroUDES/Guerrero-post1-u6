package com.tienda.pedidos.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Consultas sobre el cliente: tipo, cartera e historial de pedidos
@Repository
public class ClienteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> buscarTipoCliente(Long clienteId) {
        String tipo = jdbcTemplate.query(
            "SELECT tipo_cliente FROM clientes WHERE id = ?",
            rs -> rs.next() ? rs.getString(1) : null, clienteId);
        return Optional.ofNullable(tipo);
    }

    // Suma de facturas sin pagar; devuelve 0 si no tiene ninguna
    public double obtenerDeudaPendiente(Long clienteId) {
        Double deuda = jdbcTemplate.queryForObject(
            "SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false",
            Double.class, clienteId);
        return deuda != null ? deuda : 0.0;
    }

    public Optional<String> buscarNit(Long clienteId) {
        String nit = jdbcTemplate.query(
            "SELECT nit FROM clientes WHERE id = ?",
            rs -> rs.next() ? rs.getString(1) : null, clienteId);
        return Optional.ofNullable(nit);
    }

    public int contarPedidos(Long clienteId) {
        Integer cantidad = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pedidos WHERE cliente_id = ?", Integer.class, clienteId);
        return cantidad != null ? cantidad : 0;
    }
}
