import mpi.MPI;
import mpi.MPIException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Programa de procesamiento distribuido de reportes de tráfico usando DLML sobre MPI.
 *
 * Cada proceso:
 *  - Inserta un conjunto de archivos JSON en la lista distribuida (DLML).
 *  - Procesa los archivos asignados y construye un árbol de reportes únicos.
 *  - Calcula su número de reportes locales y participa en una reducción global.
 *
 * El proceso raíz (0):
 *  - Recolecta los árboles parciales de todos los procesos.
 *  - Consolida los reportes sin duplicados y muestra el total global.
 */
class Trafico {

    /** Suma total de reportes combinados de todos los procesos. */
    static int solTotal = 0;

    /** Estructura que almacena los reportes únicos procesados localmente. */
    static Arbol reportes = new Arbol();

    /**
     * Procesa los archivos de datos asignados al proceso actual.
     * Cada archivo contiene un arreglo JSON llamado "alerts".
     * Los identificadores únicos de cada alerta se almacenan en un TreeMap local.
     *
     * @return número de reportes procesados localmente
     * @throws MPIException si ocurre un error en la comunicación MPI
     */
    static int contar() throws MPIException {
        int jams = 0;
        Data elem;
        String key;
        TreeMap<String, String> tree = reportes.getTree();

        System.out.println(DLML.id + ": Iniciando conteo...");

        while ((elem = DLML.Get(Data.class)) != null) {
            try (InputStream is = Trafico.class.getResourceAsStream(elem.getArchivo())) {
                if (is == null) {
                    throw new NullPointerException("No se puede abrir el archivo " + elem.getArchivo());
                }

                JSONTokener tokener = new JSONTokener(is);
                JSONObject object = new JSONObject(tokener);
                JSONArray alerts = object.getJSONArray("alerts");

                for (int i = 0; i < alerts.length(); i++) {
                    key = alerts.getJSONObject(i).getString("id");
                    if (!tree.containsKey(key)) {
                        tree.put(key, ""); // valor vacío ya que no se usa 'type' actualmente
                    }
                }

                jams++;
            } catch (IOException e) {
                System.err.println("Error de lectura en " + elem.getArchivo() + ": " + e.getMessage());
            }
        }
        return jams;
    }

    /**
     * Punto de entrada principal del programa MPI.
     *
     * @param args argumentos del entorno MPI
     * @throws MPIException si ocurre un error en la inicialización o finalización MPI
     * @throws IOException  si ocurre un error de E/S
     */
    public static void main(String[] args) throws MPIException, IOException {
        int id, total, solParciales;
        double inicio, fin;
        TreeMap<String, String> reportesTotales = new TreeMap<>();

        DLML.setDataClass(Data.class);
        DLML.Init(args);

        id = DLML.id;
        total = DLML.total;

        System.out.println(id + ": Iniciando carga de archivos...");
        inicio = MPI.wtime();

        String prefijo = "waze/";
        String extension = "json";

        // Distribuye los archivos de forma balanceada entre procesos
        for (int i = id; i < 100; i += total) {
            String archivo = prefijo + i + "." + extension;
            DLML.Insert(new Data(archivo));
        }

        // Procesamiento local
        solParciales = contar();
        System.out.println(id + ": Reportes parciales: " + solParciales);

        // Reducción global
        solTotal = DLML.Reduce_Add(solParciales);

        // Recolección de árboles parciales en el proceso raíz
        ArrayList<Arbol> arboles = DLML.<Arbol>Gather(reportes);

        // Proceso raíz: consolidar resultados
        DLML.OnlyOne(() -> {
            System.out.println("Reportes totales: " + solTotal);

            for (Arbol arbolAux : arboles) {
                for (Entry<String, String> entry : arbolAux.getTree().entrySet()) {
                    reportesTotales.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }

            System.out.println("Total de entradas únicas: " + reportesTotales.size());
        });

        fin = MPI.wtime();
        System.out.printf("Tiempo total: %.3f segundos%n", (fin - inicio));

        DLML.Finalize();
    }
}

