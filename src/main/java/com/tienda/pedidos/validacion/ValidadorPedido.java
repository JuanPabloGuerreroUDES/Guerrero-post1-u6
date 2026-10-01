package com.tienda.pedidos.validacion;

// Eslabon de la cadena: cada validador decide si el pedido continua o se rechaza.
// Si un eslabon rechaza, los siguientes ya no se ejecutan.
public abstract class ValidadorPedido {

    private ValidadorPedido siguiente;

    public ValidadorPedido encadenar(ValidadorPedido siguiente) {
        this.siguiente = siguiente;
        return siguiente;
    }

    public final void validar(ContextoPedido contexto) {
        ejecutar(contexto);
        if (!contexto.isRechazado() && siguiente != null) {
            siguiente.validar(contexto);
        }
    }

    protected abstract void ejecutar(ContextoPedido contexto);
}
