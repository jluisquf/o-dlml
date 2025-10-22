import mpi.*;
import java.io.IOException;
import java.util.LinkedList;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Hilo de protocolo DLML que coordina el intercambio de datos entre procesos MPI.
 *
 * Comportamiento general:
 *  - Atiende mensajes entrantes (LISTA_VACIA, PETICION_TAM_LISTA, DAME_DATOS, etc.).
 *  - Gestiona subastas para solicitar datos a otros procesos cuando la lista local está vacía.
 *  - Distribuye datos a solicitantes remotos y actualiza banderas/sincronización en DLML.
 *
 * Notas:
 *  - La lógica y las etiquetas de mensaje se mantienen fieles al código original.
 *  - Se eliminan comentarios innecesarios y bloques comentados sin uso.
 *
 * @param <T> tipo de dato que implementa DataLike
 */
public class Protocol<T extends DataLike> extends Thread {

    private final LoadBalancingStrategy strategy = DLML.getStrategy();

    /** Clase de datos tipada tomada de la configuración global de DLML. */
    @SuppressWarnings("unchecked")
    private final Class<T> dataClass = (Class<T>) DLML.getDataClass();

    /** Mapper JSON estático y reutilizable para serialización/deserialización. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Bucle principal del protocolo. Recibe y procesa mensajes MPI
     * hasta completar la finalización acordada entre procesos.
     */
    @Override
    public void run() {
        int c, r;
        int[] m = new int[1];
        int[] dSize = new int[1];
        int[] tam = new int[1];
        byte[] dd = null;
        int[] info = new int[DLML.total];

        int max = 0;
        int maxid = 0;

        LinkedList<Integer> requests = new LinkedList<>();
        LinkedList<Integer> ptl = new LinkedList<>();

        Status status;
        int idRemote;
        int finalizeCounter = 0;
        int requestAnswers = 0;
        boolean fsubasta = false;
        boolean ffinalize = false;
        int csubastas = 0;

        try {
            while (finalizeCounter != DLML.total) {

                status = MPI.COMM_WORLD.recv(m, 1, MPI.INT, MPI.ANY_SOURCE, MPI.ANY_TAG);

                switch (status.getTag()) {
                    case DLML.LISTA_VACIA:
                        r = requests.size();
                        for (int i = 0; i < r; i++) {
                            idRemote = requests.remove();
                            m[0] = 0;
                            MPI.COMM_WORLD.send(m, 1, MPI.INT, idRemote, DLML.NO_HAY_DATOS);
                        }

                        if (DLML.flagInfo) {
                            DLML.flagInfo = false;
                            m[0] = 0;
                            for (int auxptl : ptl) {
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, auxptl, DLML.INFORMACION_LISTA);
                            }
                            ptl.clear();
                        }

                        fsubasta = true;
                        // Protocolo de subasta: pedir tamaños de listas a todos los demás
                        for (int i = 0; i < DLML.total; i++) {
                            if (i != DLML.id) {
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, i, DLML.PETICION_TAM_LISTA);
                            }
                        }
                        break;

                    case DLML.PETICION_TAM_LISTA:
                        if (!ffinalize) {
                            if (!fsubasta) {
                                DLML.flagInfo = true;
                                ptl.add(status.getSource());
                            } else {
                                m[0] = 0;
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, status.getSource(), DLML.INFORMACION_LISTA);
                            }
                        } else {
                            m[0] = 0;
                            MPI.COMM_WORLD.send(m, 1, MPI.INT, status.getSource(), DLML.INFORMACION_LISTA);
                        }
                        break;

                    case DLML.TAM_LISTA:
                        DLML.flagInfo = false;
                        m[0] = DLML.data.size();
                        for (int auxptl : ptl) {
                            MPI.COMM_WORLD.send(m, 1, MPI.INT, auxptl, DLML.INFORMACION_LISTA);
                        }
                        ptl.clear();
                        DLML.mutex.release();
                        break;

                    case DLML.INFORMACION_LISTA:
                        info[status.getSource()] = m[0];
                        requestAnswers++;

                        if (requestAnswers == (DLML.total - 1)) {
                            requestAnswers = 0;


                            int donor = strategy.selectDonor(info, DLML.id);


			    System.out.println("DONOR: "+donor + "SUBASTAS: "+csubastas);
                            //max = 0;
                            //for (int i = 0; i < DLML.total; i++) {
                            //    if (i != DLML.id && info[i] > max) {
                            //        max = info[i];
                            //        maxid = i;
                            //    }
                            //}

                            if (donor > 0) {
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, donor, DLML.DAME_DATOS);
                                csubastas = 0;
                            } else {
                                // No hay datos en otros procesos: reintentar subasta unas veces,
                                // luego finalizar protocolo localmente y notificar a otros.
                                if (csubastas < 2) {
                                    System.out.println("DLML.id "+DLML.id+ " csubastas "+csubastas);
                                    MPI.COMM_WORLD.send(m, 1, MPI.INT, DLML.id, DLML.LISTA_VACIA);
                                    csubastas++;
                                } else {
                                    System.out.println("DLML.id "+DLML.id+ " FINALIZANDO.....");
                                    m[0] = 0;
                                    DLML.mutex.release();

                                    for (int i = 0; i < DLML.total; i++) {
                                        if (i != DLML.id) {
                                            MPI.COMM_WORLD.send(m, 1, MPI.INT, i, DLML.FINALIZE);
                                        }
                                    }
                                    finalizeCounter++;

                                    r = requests.size();
                                    for (int i = 0; i < r; i++) {
                                        idRemote = requests.remove();
                                        m[0] = 0;
                                        MPI.COMM_WORLD.send(m, 1, MPI.INT, idRemote, DLML.NO_HAY_DATOS);
                                    }

                                    m[0] = 0;
                                    for (int auxptl : ptl) {
                                        MPI.COMM_WORLD.send(m, 1, MPI.INT, auxptl, DLML.INFORMACION_LISTA);
                                    }
                                }
                            }
                            //for (int i = 0; i < info.length; i++) info[i] = 0;
                        }
                        break;

                    case DLML.DATOS_REMOTOS:
                        int idAux = status.getSource();
                        fsubasta = false;

                        for (int i = 0; i < m[0]; i++) {
                            MPI.COMM_WORLD.recv(dSize, 1, MPI.INT, idAux, DLML.TAM_BUFFER);
                            dd = new byte[dSize[0]];
                            MPI.COMM_WORLD.recv(dd, dSize[0], MPI.BYTE, idAux, DLML.DATOS_REMOTOS);
                            T data = MAPPER.readValue(dd, dataClass);
                            DLML.data.add(data);
                        }
                        m[0] = 1;
                        DLML.mutex.release();
                        break;

                    case DLML.LISTA_DE_DATOS:
                        if (DLML.data.size() >= (requests.size() + 1)) {
                            // Más datos que peticiones: repartir en lotes equilibrados
                            c = DLML.data.size() / (requests.size() + 1);
                            r = requests.size();
                            for (int i = 0; i < r; i++) {
                                idRemote = requests.remove();
                                m[0] = c;
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, idRemote, DLML.DATOS_REMOTOS);
                                for (int j = 0; j < c; j++) {
                                    @SuppressWarnings("unchecked")
                                    T aux = (T) DLML.data.removeFirst();
                                    dd = MAPPER.writeValueAsBytes(aux);
                                    tam[0] = dd.length;
                                    MPI.COMM_WORLD.send(tam, 1, MPI.INT, idRemote, DLML.TAM_BUFFER);
                                    MPI.COMM_WORLD.send(dd, dd.length, MPI.BYTE, idRemote, DLML.DATOS_REMOTOS);
                                }
                            }
                            DLML.flag = false;
                            DLML.mutex.release();

                        } else {
                            // Menos datos que peticiones: enviar 1 a tantos como sea posible
                            r = DLML.data.size();
                            for (int i = 0; i < (r - 1); i++) {
                                idRemote = requests.remove();
                                m[0] = 1;
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, idRemote, DLML.DATOS_REMOTOS);
                                @SuppressWarnings("unchecked")
                                T aux = (T) DLML.data.removeFirst();
                                dd = MAPPER.writeValueAsBytes(aux);
                                tam[0] = dd.length;
                                MPI.COMM_WORLD.send(tam, 1, MPI.INT, idRemote, DLML.TAM_BUFFER);
                                MPI.COMM_WORLD.send(dd, dd.length, MPI.BYTE, idRemote, DLML.DATOS_REMOTOS);
                            }
                            r = requests.size();
                            for (int i = 0; i < r; i++) {
                                idRemote = requests.remove();
                                m[0] = 0;
                                MPI.COMM_WORLD.send(m, 1, MPI.INT, idRemote, DLML.NO_HAY_DATOS);
                            }
                            DLML.flag = false;
                            DLML.mutex.release();
                        }
                        break;

                    case DLML.DAME_DATOS:
                        if (!ffinalize) {
                            DLML.flag = true;
                            requests.add(status.getSource());
                        } else {
                            MPI.COMM_WORLD.send(m, 1, MPI.INT, status.getSource(), DLML.NO_HAY_DATOS);
                        }
                        break;

                    case DLML.NO_HAY_DATOS:
                        fsubasta = false;
                        MPI.COMM_WORLD.send(m, 1, MPI.INT, DLML.id, DLML.LISTA_VACIA);
                        break;

                    case DLML.FINALIZE:
                        finalizeCounter++;
                        break;

                    default:
                        System.out.println("MENSAJE NO RECONOCIDO: " + status.getTag()
                                + " de " + status.getSource());
                }
            }

            MPI.COMM_WORLD.barrier();
            DLML.mutexEnd.release();

        } catch (MPIException e) {
            System.err.println("Error MPI en Protocol.run(): " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error de IO/JSON en Protocol.run(): " + e.getMessage());
        }
        // Fin del protocolo DLML
    }
}

