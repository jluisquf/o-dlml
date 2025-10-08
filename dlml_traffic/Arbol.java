// Arbol.java
import java.util.Objects;
import java.util.TreeMap;

/**
 * Estructura de apoyo que mantiene un mapa ordenado de reportes.
 * Es un POJO compatible con Jackson (constructor vacio y getters/setters).
 */
public class Arbol {

    /** Mapa ordenado de id -> valor asociado (por ahora, cadena vacia). */
    private TreeMap<String, String> tree = new TreeMap<>();

    /** Constructor por defecto requerido por bibliotecas de serializacion. */
    public Arbol() {
    }

    /**
     * Obtiene el mapa de reportes.
     *
     * @return instancia de TreeMap con los reportes
     */
    public TreeMap<String, String> getTree() {
        return tree;
    }

    /**
     * Establece el mapa de reportes.
     *
     * @param tree nuevo mapa; si es null se reemplaza por un TreeMap vacio
     */
    public void setTree(TreeMap<String, String> tree) {
        this.tree = (tree != null) ? tree : new TreeMap<>();
    }

    /**
     * Cantidad de entradas almacenadas.
     *
     * @return numero de claves en el mapa
     */
    public int size() {
        return tree.size();
    }

    @Override
    public String toString() {
        return "Arbol{size=" + tree.size() + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(tree);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Arbol)) return false;
        Arbol other = (Arbol) obj;
        return Objects.equals(this.tree, other.tree);
    }
}

