import java.util.Arrays;
import java.util.Objects;

/**
 * Representa el estado de un tablero en el problema de las N reinas.
 * 
 * Cada objeto almacena:
 *  - Un arreglo de tamaño TAM que indica, por renglón (índice 0..TAM-1),
 *    la columna (1..TAM) donde se colocó una reina.
 *  - El número de renglón actual que se está procesando.
 *
 * Esta clase es un POJO que implementa DataLike y es serializable
 * mediante Jackson para el intercambio distribuido con DLML.
 */
public class Data implements DataLike {

    /** Tamaño del tablero (número de reinas). */
    public static final int TAM = 15;

    /** Arreglo que representa la posición de las reinas por renglón. */
    private int[] tablero = new int[TAM];

    /** Renglón actual a procesar (1..TAM). */
    private int renglon;

    /** Constructor por defecto (requerido por Jackson). */
    public Data() {
    }

    /**
     * Constructor que inicializa un estado con el renglón inicial.
     *
     * @param renglon número de renglón inicial
     */
    public Data(int renglon) {
        this.renglon = renglon;
    }

    /**
     * Constructor que copia el tablero existente y el renglón actual.
     *
     * @param tablero arreglo con posiciones de reinas
     * @param renglon renglón actual
     */
    public Data(int[] tablero, int renglon) {
        this.tablero = (tablero != null) ? tablero : new int[TAM];
        this.renglon = renglon;
    }

    /**
     * Devuelve el tablero de posiciones.
     *
     * @return arreglo de enteros con las posiciones de las reinas
     */
    public int[] getTablero() {
        return tablero;
    }

    /**
     * Asigna un nuevo tablero de posiciones.
     *
     * @param tablero nuevo arreglo; si es null se asigna uno vacío
     */
    public void setTablero(int[] tablero) {
        this.tablero = (tablero != null) ? tablero : new int[TAM];
    }

    /**
     * Devuelve el número de renglón actual.
     *
     * @return renglón actual (1..TAM)
     */
    public int getRenglon() {
        return renglon;
    }

    /**
     * Establece el número de renglón actual.
     *
     * @param renglon nuevo valor del renglón
     */
    public void setRenglon(int renglon) {
        this.renglon = renglon;
    }

    /**
     * Representación textual del tablero.
     *
     * @return cadena con las posiciones de las reinas
     */
    @Override
    public String toString() {
        return Arrays.toString(tablero);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(tablero), renglon);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Data)) return false;
        Data other = (Data) obj;
        return renglon == other.renglon && Arrays.equals(tablero, other.tablero);
    }
}

