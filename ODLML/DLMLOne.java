// DLMLOne.java
/**
 * Accion a ejecutar una unica vez en el proceso raiz.
 * Se utiliza con DLML.OnlyOne(...).
 */
@FunctionalInterface
interface DLMLOne {

    /**
     * Ejecuta la accion definida por el usuario en el proceso raiz.
     */
    void run();
}

