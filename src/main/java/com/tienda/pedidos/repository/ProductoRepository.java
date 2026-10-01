package com.tienda.pedidos.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Consultas de catalogo (precio) e inventario (stock) de un producto
@Repository
public class ProductoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Devuelve 0 si el producto no tiene registro de inventario
    public int obtenerStock(Long productoId) {
        Integer stock = jdbcTemplate.query(
            "SELECT stock FROM inventario WHERE producto_id = ?",
            rs -> rs.next() ? rs.getInt(1) : null, productoId);
        return stock != null ? stock : 0;
    }

    public double obtenerPrecio(Long productoId) {
        return jdbcTemplate.queryForObject(
            "SELECT precio FROM productos WHERE id = ?", Double.class, productoId);
    }
}
