package com.redxela.processing;

import com.redxela.domain.CompletedOrder;
import java.util.function.Consumer;

/**
 * Quien quiere enterarse de cómo termina cada pedido que se empezó a procesar. Se invoca en el hilo
 * del pedido, así que debe ser rápido y seguro entre hilos. Cada método tiene una implementación
 * vacía por defecto: quien escucha implementa solo lo que le interesa.
 */
public interface OrderLifecycleListener {

    /** El pedido se despachó. */
    default void onCompleted(CompletedOrder order) {
    }

    /** El pedido se interrumpió o falló antes de despacharse; ya liberó todos sus recursos. */
    default void onAborted(String orderId) {
    }

    /** Listener que solo atiende los pedidos despachados. */
    static OrderLifecycleListener onCompleted(Consumer<CompletedOrder> action) {
        return new OrderLifecycleListener() {
            @Override
            public void onCompleted(CompletedOrder order) {
                action.accept(order);
            }
        };
    }
}
