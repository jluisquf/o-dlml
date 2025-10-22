#!/bin/bash
set -e

# === Ajusta si tu OpenMPI está en otra ruta (debe ser 4.x con Java) ===
OPENMPI_HOME=${OPENMPI_HOME:-/opt/openmpi-5.0.8}
# =====================================================================

#SRC_FILES="DataLike.java DLML.java DLMLOne.java Protocol.java"
SRC_FILES="AuctionStrategy.java  LoadBalancingStrategy.java Protocol.java RoundRobinStrategy.java StrategyFactory.java StrategyType.java WorkStealingStrategy.java DLML.java DLMLOne.java DataLike.java"
BUILD_DIR="build-dlml"
DIST_DIR="dist"
VER="1.0"

rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR" "$DIST_DIR"

echo ">> Compilando DLML..."
mpijavac -cp "$OPENMPI_HOME/lib/mpi.jar:lib/*" -d "$BUILD_DIR" $SRC_FILES

echo ">> Empaquetando JAR delgado (solo clases DLML)..."
jar cf "$DIST_DIR/dlml-$VER.jar" -C "$BUILD_DIR" .
echo "   -> $DIST_DIR/dlml-$VER.jar"

echo ">> Construyendo fat-jar (DLML + lib/*)..."
TMP="build-fat-tmp"              # usa un tmp fuera de build-dlml
mkdir -p "$TMP"

cp -r "$BUILD_DIR"/. "$TMP"/

# Desempaca todas las dependencias dentro del fat-jar
for J in lib/*.jar; do
  [ -e "$J" ] || continue
  (cd "$TMP" && jar xf "../$J") || true
done

# Evita conflictos de metadatos
rm -rf "$TMP/META-INF" 2>/dev/null || true

jar cf "$DIST_DIR/dlml-$VER-all.jar" -C "$TMP" .
echo "   -> $DIST_DIR/dlml-$VER-all.jar"

echo "OK. DLML listo en dist/"
