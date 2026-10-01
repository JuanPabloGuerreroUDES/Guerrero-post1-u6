package com.tienda.pedidos.validacion;

import com.tienda.pedidos.repository.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Optional;

// Eslabon 2: el cliente debe existir y, si es moroso, no puede tener deuda
// salvo que el pedido llegue despues de la hora de corte
@Component
public class ValidadorCliente extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorCliente.class);
    private static final LocalTime HORA_DE_CORTE = LocalTime.of(20, 0);

    private final ClienteRepository clienteRepository;

    public ValidadorCliente(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        Long clienteId = contexto.getRequest().getClienteId();
        Optional<String> tipo = clienteRepository.buscarTipoCliente(clienteId);
        if (tipo.isEmpty()) {
            log.warn("Cliente no encontrado: {}", clienteId);
            contexto.rechazar("Cliente no registrado");
            return;
        }
        contexto.setTipoCliente(tipo.get());

        if (tipo.get().equals("MOROSO")) {
            validarMora(contexto, clienteId);
        }
    }

    private void validarMora(ContextoPedido contexto, Long clienteId) {
        double deuda = clienteRepository.obtenerDeudaPendiente(clienteId);
        if (deuda <= 0) {
            return;
        }
        if (LocalTime.now().isBefore(HORA_DE_CORTE)) {
            log.warn("Cliente moroso con deuda pendiente: {}", deuda);
            contexto.rechazar("Cliente con deuda pendiente: $" + deuda);
        } else {
            log.info("Cliente moroso fuera del horario de corte; se permite el pedido excepcionalmente");
        }
    }
}
