import mpi.MPIException;
import java.util.concurrent.TimeUnit;

/**
 * Resuelve el problema de las N reinas de forma distribuida usando DLML sobre MPI.
 *
 * Estrategia:
 *  - Proceso raiz inserta el primer estado en la lista distribuida (DLML).
 *  - Cada proceso extrae estados (tableros parciales) y prueba colocar una reina
 *    en cada columna del renglón actual, verificando que no haya conflicto.
 *  - Los estados válidos se reinsertan en DLML para su exploración (búsqueda en anchura).
 *  - Cuando un tablero está completo (renglon == TAM), se contabiliza una solución local.
 *  - Se realiza una reducción por suma para obtener el total global de soluciones.
 *
 * Requisitos sobre la clase Data:
 *  - Debe exponer: int TAM (tamaño del tablero), int getRenglon(), void setRenglon(int),
 *    int[] getTablero(), constructor Data(int renglonInicial), y constructor Data(int[] tablero, int renglon).
 */
class Application {

    /** Numero total de soluciones globales (tras la reduccion). */
    static int solTotal = 0;

    /**
     * Verifica si colocar una reina en la columna {@code col} del renglón actual
     * no entra en conflicto con las reinas previamente colocadas.
     *
     * Conflictos comprobados:
     *  - Misma columna.
     *  - Diagonal principal.
     *  - Diagonal secundaria.
     *
     * Nota: El arreglo de tablero almacena, por renglón (indice 0..TAM-1),
     * la columna (1..TAM) en la que se colocó la reina.
     *
     * @param col     columna propuesta (1..Data.TAM)
     * @param tablero estado parcial del tablero (con renglón actual ya apuntando a la fila de trabajo)
     * @return true si hay conflicto (la reina seria comida), false si es seguro colocarla
     */
    static boolean esComida(int col, Data tablero) {
        boolean comida = true;
        int i, j;
        int reng = tablero.getRenglon() - 1; // fila previa a la que intentamos llenar

        // Verifica columna repetida en filas anteriores
        i = reng - 1;
        while ((i >= 0) && (tablero.getTablero()[i] != col)) {
            i--;
        }
        if (i < 0) {
            // Verifica diagonal principal (↘)
            i = reng - 1;
            j = col - 1;
            while ((i >= 0) && (j >= 0) && (tablero.getTablero()[i] != j)) {
                i--;
                j--;
            }
            if ((i < 0) || (j < 0)) {
                // Verifica diagonal secundaria (↗)
                i = reng - 1;
                j = col + 1;
                while ((i >= 0) && (j <= Data.TAM) && (tablero.getTablero()[i] != j)) {
                    i--;
                    j++;
                }
                if ((i < 0) || (j > Data.TAM)) {
                    comida = false; // no se encontraron conflictos
                }
            }
        }
        return comida;
    }

    /**
     * Explora el espacio de soluciones a partir de los estados extraidos de DLML.
     * Para cada estado, intenta colocar una reina en cada columna valida del renglón actual.
     * - Si el tablero aun no se completa, inserta el nuevo estado en DLML.
     * - Si se completa, incrementa el conteo local de soluciones.
     *
     * @return numero de soluciones encontradas por este proceso
     * @throws MPIException si ocurre un error en la interaccion con DLML/MPI
     */
    static int calcularReinas() throws MPIException {
        int numSol = 0;
        Data elem;
        while ((elem = DLML.Get(Data.class)) != null) {
            for (int col = 1; col <= Data.TAM; col++) {
                if (elem.getRenglon() < Data.TAM) {
                    if (!esComida(col, elem)) {
                        // Coloca reina y avanza renglón (clona estado para reinsertarlo)
                        elem.getTablero()[elem.getRenglon() - 1] = col;
                        elem.setRenglon(elem.getRenglon() + 1);

                        Data nuevo = new Data(elem.getTablero().clone(), elem.getRenglon());
                        DLML.Insert(nuevo);

                        // Reestablece renglón (backtrack local del objeto compartido)
                        elem.setRenglon(elem.getRenglon() - 1);
                    }
                } else {
                    if (!esComida(col, elem)) {
                        elem.getTablero()[elem.getRenglon() - 1] = col;
                        numSol += 1; // tablero completo y valido
                    }
                }
            }
        }
        return numSol;
    }

    /**
     * Punto de entrada del programa.
     * 1) Configura la clase de datos y arranca DLML/MPI.
     * 2) Inserta el estado inicial desde el proceso raiz.
     * 3) Explora el arbol de busqueda y reduce el total de soluciones.
     * 4) Imprime resultados y tiempo de ejecucion.
     *
     * @param args argumentos del entorno MPI
     * @throws MPIException si ocurre un error de MPI
     */
    public static void main(String[] args) throws MPIException {
        DLML.setDataClass(Data.class);
        DLML.Init(args);

        // Estado inicial: renglón 1 vacío
        DLML.OnlyOne(() -> DLML.Insert(new Data(1)));

        long inicio = System.nanoTime();
        int solParciales = calcularReinas();
        solTotal = DLML.Reduce_Add(solParciales);
        long fin = System.nanoTime();

        DLML.OnlyOne(() -> System.out.println("Solucion total: " + solTotal));

        long segundos = TimeUnit.SECONDS.convert(fin - inicio, TimeUnit.NANOSECONDS);
        System.out.println("Tiempo segundos: " + segundos);

        DLML.Finalize();
    }
}

