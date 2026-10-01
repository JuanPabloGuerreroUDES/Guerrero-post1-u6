package com.tienda.pedidos.descuento;

// Marca las estrategias que son campanas promocionales. Spring inyecta todas las que existan
// en CalculadorDescuentoFinal, asi que una campana nueva solo necesita su propia clase.
public interface CampanaDescuento extends EstrategiaDescuento {
}
