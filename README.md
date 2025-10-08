#  Descarga del proyecto

```bash
git clone https://github.com/jluisquf/o-dlml.git
```

#  Generación de los jar de la biblioteca

```bash
de o-dlml
cd ODLML
./build-dlml.sh
```

# Configurar variables de ambiente

```bash
export PATH=/opt/openmpi-5.0.8/bin:$PATH
export LD_LIBRARY_PATH=/opt/openmpi-5.0.8/lib:$LD_LIBRARY_PATH
export JAVA_HOME=/opt/jdk-21.0.1
export PATH=$JAVA_HOME/bin:$PATH
```
# Compilación y ejecuión de programa ejemplo

```bash
mpijavac -cp "../ODLML/dist/dlml-1.0-all.jar:." Data.java Application.java
mpirun -np 8 java  -cp "../ODLML/dist/dlml-1.0-all.jar:." Application
```
