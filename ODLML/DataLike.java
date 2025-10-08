// DataLike.java
/**
 * Interfaz marcadora para los tipos de datos intercambiados por DLML/Protocol.
 *
 * Recomendaciones para las clases que implementen esta interfaz:
 * - Deben ser POJOs compatibles con la serializacion/deserializacion de Jackson.
 * - Declarar constructor sin argumentos.
 * - Incluir getters y setters para todos los campos.
 * - Opcional: sobrescribir toString(), equals() y hashCode() si aplica.
 */
public interface DataLike {
    // Interfaz marcadora sin metodos
}

