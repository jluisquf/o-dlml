import mpi.MPI;
import mpi.MPIException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.function.BinaryOperator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DLML: utilidades de ejecucion distribuida sobre MPI integradas con una cola de datos.
 *
 * Notas importantes:
 *  - Esta clase asume la existencia de un hilo auxiliar {@code Protocol} que coordina
 *    la disponibilidad de datos a traves de mensajes MPI y los semaforos internos.
 *  - No se modifica la interfaz publica ni la esencia del flujo original.
 *
 * @param <T> tipo de dato que implementa {@link DataLike}
 */
class DLML<T extends DataLike> {

    /** Cola de datos compartida. El acceso se coordina via {@link Protocol} y semaforos. */
    static final LinkedList<DataLike> data = new LinkedList<>();

    // Etiquetas de mensajes MPI
    static final int NO_HAY_DATOS       = 100;
    static final int PETICION_TAM_LISTA = 101;
    static final int INFORMACION_LISTA  = 102;
    static final int DAME_DATOS         = 103;
    static final int DATOS              = 104;
    static final int LISTA_VACIA        = 105;
    static final int DATOS_REMOTOS      = 106;
    static final int TAM_BUFFER         = 107;
    static final int LISTA_DE_DATOS     = 108;
    static final int FINALIZE           = 109;
    static final int TAM_LISTA          = 110;

    /** Rank raiz (convencion). */
    static final int ROOT = 0;

    /** Flags de control intercambiadas con el hilo de protocolo. */
    static boolean flag = false;
    static boolean flagInfo = false;
    static boolean flagEnd = false;

    /** Identificador del proceso (rank) y total de procesos. */
    static int id;
    static int total;

    /** Hilo de protocolo que coordina la produccion/consumo de datos. */
    static Protocol protocol = null;

    /** Semaforos para sincronizacion con el hilo de protocolo. */
    static final Semaphore mutex = new Semaphore(0);
    static final Semaphore mutexEnd = new Semaphore(0);

    /** Clase de datos para conversion tipada (opcional). */
    static Class<? extends DataLike> DATA_CLASS;

    /** Mapper JSON reutilizable para reducir overhead de creacion. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Establece la clase de datos por defecto a utilizar en conversiones tipadas.
     *
     * @param cls clase que implementa {@link DataLike}
     */
    public static void setDataClass(Class<? extends DataLike> cls) {
        DATA_CLASS = cls;
    }

    /**
     * Obtiene la clase de datos por defecto.
     *
     * @return clase configurada o null si no se ha establecido
     */
    public static Class<? extends DataLike> getDataClass() {
        return DATA_CLASS;
    }

    /**
     * Inicializa MPI y arranca el hilo de protocolo.
     *
     * @param args argumentos de linea de comandos de MPI
     * @throws MPIException si ocurre un error en la inicializacion de MPI
     */
    public static void Init(String[] args) throws MPIException {
        protocol = new Protocol();
        MPI.InitThread(args, MPI.THREAD_MULTIPLE);
        id = MPI.COMM_WORLD.getRank();
        total = MPI.COMM_WORLD.getSize();
        protocol.start();
    }

    /**
     * Finaliza el hilo de protocolo (espera su terminacion) y cierra MPI.
     *
     * @throws MPIException si ocurre un error al finalizar MPI
     */
    public static void Finalize() throws MPIException {
        try {
            if (protocol != null) {
                protocol.join();
            }
        } catch (InterruptedException ignored) {
            // Se preserva comportamiento original (sin reinterrumpir ni registrar)
        }
        MPI.Finalize();
    }

    /**
     * Obtiene un elemento de la cola de datos de manera tipada.
     * El metodo puede devolver null para indicar que no hay mas datos disponibles
     * y que se ha coordinado el cierre con el hilo de protocolo.
     *
     * @param cls clase del tipo concreto que extiende {@link DataLike}
     * @param <T> tipo concreto solicitado
     * @return elemento de tipo T, o null si no hay mas datos
     * @throws MPIException si ocurre un error de comunicacion con MPI
     */
    public static <T extends DataLike> T Get(Class<T> cls) throws MPIException {
        if (cls == null) {
            throw new IllegalArgumentException("La clase de destino no debe ser null");
        }

        int[] m = new int[1];

        if (flagEnd) {
            flagEnd = false;
            protocol = new Protocol();
            protocol.start();
        }

        if (flagInfo) {
            MPI.COMM_WORLD.send(m, 1, MPI.INT, id, TAM_LISTA);
            try { mutex.acquire(); } catch (InterruptedException ignored) {}
        }

        if (!flag) {
            if (!data.isEmpty()) {
                return cls.cast(data.removeFirst());
            } else {
                MPI.COMM_WORLD.send(m, 1, MPI.INT, id, LISTA_VACIA);
                try { mutex.acquire(); } catch (InterruptedException ignored) {}

                if (!data.isEmpty()) {
                    return cls.cast(data.removeFirst());
                } else {
                    flagEnd = true;
                    try { mutexEnd.acquire(); } catch (InterruptedException ignored) {}
                    return null;
                }
            }
        } else {
            if (!data.isEmpty()) {
                MPI.COMM_WORLD.send(m, 1, MPI.INT, id, LISTA_DE_DATOS);
                try { mutex.acquire(); } catch (InterruptedException ignored) {}
                return cls.cast(data.removeFirst());
            } else {
                MPI.COMM_WORLD.send(m, 1, MPI.INT, id, LISTA_VACIA);
                try { mutex.acquire(); } catch (InterruptedException ignored) {}

                if (!data.isEmpty()) {
                    return cls.cast(data.removeFirst());
                } else {
                    flagEnd = true;
                    try { mutexEnd.acquire(); } catch (InterruptedException ignored) {}
                    return null;
                }
            }
        }
    }

    /**
     * Inserta un elemento al inicio de la cola de datos.
     *
     * @param a elemento a insertar
     * @param <T> tipo que extiende {@link DataLike}
     */
    public static <T extends DataLike> void Insert(T a) {
        data.addFirst(a);
    }

    /**
     * Reduccion por suma de enteros. Acumula las contribuciones de todos los procesos.
     * Mantiene el esquema de uso de gather del codigo original.
     *
     * @param value entero local
     * @return suma total en el proceso raiz
     * @throws MPIException si ocurre un error de comunicacion con MPI
     */
    public static int Reduce_Add(int value) throws MPIException {
        int acc = 0;
        int[] buffer = new int[DLML.total];

        buffer[0] = value;
        MPI.COMM_WORLD.gather(buffer, 1, MPI.INT, ROOT);
        for (int i = 0; i < total; i++) {
            acc += buffer[i];
        }
        return acc;
    }

    /**
     * Reduccion por suma de dobles. Acumula las contribuciones de todos los procesos.
     *
     * @param value doble local
     * @return suma total en el proceso raiz
     * @throws MPIException si ocurre un error de comunicacion con MPI
     */
    public static double Reduce_Add(double value) throws MPIException {
        double acc = 0.0;
        double[] buffer = new double[DLML.total];

        buffer[0] = value;
        MPI.COMM_WORLD.gather(buffer, 1, MPI.DOUBLE, ROOT);
        for (int i = 0; i < total; i++) {
            acc += buffer[i];
        }
        return acc;
    }

    /**
     * Reduccion por suma de flotantes. Acumula las contribuciones de todos los procesos.
     *
     * @param value flotante local
     * @return suma total en el proceso raiz
     * @throws MPIException si ocurre un error de comunicacion con MPI
     */
    public static float Reduce_Add(float value) throws MPIException {
        float acc = 0.0f;
        float[] buffer = new float[DLML.total];

        buffer[0] = value;
        MPI.COMM_WORLD.gather(buffer, 1, MPI.FLOAT, ROOT);
        for (int i = 0; i < total; i++) {
            acc += buffer[i];
        }
        return acc;
    }



    /**
     * Reduce (en root) un objeto local con el de los demás procesos usando un combinador.
     * Cada proceso envía su objeto serializado; el root acumula aplicando 'op'.
     *
     * @param local  objeto local
     * @param cls    clase del objeto (para deserializar en root)
     * @param op     combinador asociativo (a,b) -> resultado
     * @return en root, el resultado acumulado; en procesos no-root, devuelve 'local'
    */
    public static <T> T Reduce(T local, Class<T> cls, BinaryOperator<T> op)
        throws MPIException, IOException {

        byte[] dd;
        int[] tam = new int[1];

        if (DLML.id == ROOT) {
            T acc = local;
            for (int i = 1; i < total; i++) {
                MPI.COMM_WORLD.recv(tam, 1, MPI.INT, i, TAM_BUFFER);
                dd = new byte[tam[0]];
                MPI.COMM_WORLD.recv(dd, tam[0], MPI.BYTE, i, DATOS_REMOTOS);
                T other = MAPPER.readValue(dd, cls);
                acc = op.apply(acc, other);
            }
            return acc;
        } else {
            dd = MAPPER.writeValueAsBytes(local);
            tam[0] = dd.length;
            MPI.COMM_WORLD.send(tam, 1, MPI.INT, ROOT, TAM_BUFFER);
            MPI.COMM_WORLD.send(dd, dd.length, MPI.BYTE, ROOT, DATOS_REMOTOS);
            return local; // en no-root retornamos lo local (o null si prefieres)
        }
    }



    /**
     * Recolecta un objeto de cada proceso en el raiz y lo devuelve como lista.
     * En el proceso NO raiz, el objeto se serializa a JSON y se envia al raiz.
     * En el proceso raiz, se recibe cada objeto, se deserializa y se agrega a la lista.
     *
     * @param o objeto local a enviar o agregar (en el raiz)
     * @param <T> tipo inferido para el resultado en el raiz
     * @return en el proceso raiz, una lista con un objeto por proceso; en otros procesos, una lista vacia
     * @throws MPIException si ocurre un error de comunicacion con MPI
     * @throws IOException si hay un problema de serializacion/deserializacion
     */
    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> Gather(Object o) throws MPIException, IOException {
        ArrayList<T> result = new ArrayList<>();
        byte[] dd = null;
        int[] tam = new int[1];

        if (DLML.id == ROOT) {
            result.add((T) o);
            for (int i = 1; i < total; i++) {
                MPI.COMM_WORLD.recv(tam, 1, MPI.INT, i, TAM_BUFFER);
                dd = new byte[tam[0]];
                MPI.COMM_WORLD.recv(dd, tam[0], MPI.BYTE, i, DATOS_REMOTOS);
                Object aux = MAPPER.readValue(dd, o.getClass());
                result.add((T) aux);
            }
        } else {
            dd = MAPPER.writeValueAsBytes(o);
            tam[0] = dd.length;
            MPI.COMM_WORLD.send(tam, 1, MPI.INT, ROOT, TAM_BUFFER);
            MPI.COMM_WORLD.send(dd, dd.length, MPI.BYTE, ROOT, DATOS_REMOTOS);
        }
        return result;
    }

    /**
     * Ejecuta el runnable suministrado solo en el proceso raiz.
     *
     * @param r accion a ejecutar una unica vez en el proceso raiz
     */
    public static void OnlyOne(DLMLOne r) {
        if (DLML.id == ROOT && r != null) {
            r.run();
        }
    }
}

