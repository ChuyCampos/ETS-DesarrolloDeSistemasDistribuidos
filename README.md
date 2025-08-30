# Sistema Distribuido de Búsqueda de Frases identicas

## Opciones de Despliegue

### Opción 1: Docker Compose (Recomendado)

#### Requisitos

- Docker
- Docker Compose

#### Inicio

```bash
docker-compose build
docker-compose up
```

### Opción 2: Ejecución Tradicional

#### Requisitos

- Java JDK 11+

#### Manual

```bash
javac *.java
java ConsoleApp
```

## Acceso a la Aplicación

- Abrir navegador en: http://localhost:8080
- Introducir número de palabras (ej: 4)
- Hacer clic en "Buscar Frases Coincidentes"

## Arquitectura

### Docker Compose

- **web-server**: WebServer (8080) - Coordinador e interfaz web
- **processing-server-1**: ProcessingServer (8081) - Procesa 1/3 de los libros
- **processing-server-2**: ProcessingServer (8082) - Procesa 1/3 de los libros
- **processing-server-3**: ProcessingServer (8083) - Procesa 1/3 de los libros

### Ejecución Tradicional

- **WebServer** (8080): Coordinador e interfaz web con pool de hilos
- **ProcessingServer 1** (8081): Procesa 1/3 de los libros
- **ProcessingServer 2** (8082): Procesa 1/3 de los libros
- **ProcessingServer 3** (8083): Procesa 1/3 de los libros

## Ejemplo de Resultado

```
En "El viejo y el mar" (Hemingway) y "Cien años de soledad" (García Márquez) aparece: "en el mismo lugar"
```
