import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ConsoleApp {
    private static final Scanner scanner = new Scanner(System.in);
    private static Process webServerProcess;
    private static Process[] processingServerProcesses = new Process[3];
    private static boolean serversRunning = false;
    
    public static void main(String[] args) {
        System.out.println("    SISTEMA DISTRIBUIDO DE BÚSQUEDA DE SECUENCIAS IDENTICAS DE N PALABRAS ");
        System.out.println();
        
        showMainMenu();
    }
    
    private static void showMainMenu() {
        while (true) {
            System.out.println("\n--- MENÚ PRINCIPAL ---");
            System.out.println("1. Iniciar servidores");
            System.out.println("2. Detener servidores");
            System.out.println("3. Estado de servidores");
            System.out.println("4. Ayuda");
            System.out.println("5. Salir");
            System.out.print("\nSelecciona una opción: ");
            
            String option = scanner.nextLine().trim();
            
            switch (option) {
                case "1":
                    startServers();
                    break;
                case "2":
                    stopServers();
                    break;
                case "3":
                    showServerStatus();
                    break;
                case "4":
                    showHelp();
                    break;
                case "5":
                    System.out.println("Deteniendo servidores antes de salir...");
                    stopServers();
                    System.out.println("¡Hasta luego!");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Opción no válida. Por favor selecciona del 1 al 5.");
            }
        }
    }
    
    private static void startServers() {
        if (serversRunning) {
            System.out.println("Los servidores ya están ejecutándose.");
            return;
        }
        
        try {
            // Compilar Archivos Java si es necesario
            System.out.println("Compilando código fuente...");
            compileJavaFiles();
            
            // Iniciar los 3 servidores 
            for (int i = 1; i <= 3; i++) {
                ProcessBuilder pb = new ProcessBuilder("java", "ProcessingServer", String.valueOf(i));
                pb.directory(new File("."));
                processingServerProcesses[i-1] = pb.start();
                System.out.println("- Servidor de procesamiento " + i + " iniciado (puerto " + (8080 + i) + ")");
                Thread.sleep(2000); // Esperar un poco entre servidores
            }
            
            // Esperar un poco más para que los servidores de procesamiento se estabilicen
            System.out.println("Esperando que los servidores de procesamiento se estabilicen...");
            Thread.sleep(3000);
            
            // Iniciar el servidor web principal
            System.out.println("Iniciando servidor web principal...");
            ProcessBuilder webPb = new ProcessBuilder("java", "WebServer");
            webPb.directory(new File("."));
            webServerProcess = webPb.start();
            
            // Capturar y mostrar la salida del servidor web
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(webServerProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[WebServer] " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error leyendo salida del servidor web: " + e.getMessage());
                }
            }).start();
            
            Thread.sleep(2000);
            serversRunning = true;
            
            System.out.println("\n✅ SISTEMA INICIADO EXITOSAMENTE");
            System.out.println("┌─────────────────────────────────────────┐");
            System.out.println("│  Accede a: http://localhost:8080       │");
            System.out.println("│  Para usar la interfaz web             │");
            System.out.println("└─────────────────────────────────────────┘");
            System.out.println();
            
            // Mostrar información adicional sobre los servidores
            showServerDetails();
            
        } catch (Exception e) {
            System.err.println("Error iniciando servidores: " + e.getMessage());
            stopServers();
        }
    }
    
    private static void compileJavaFiles() throws IOException, InterruptedException {
        Process compileProcess = new ProcessBuilder("javac", "*.java").start();
        int exitCode = compileProcess.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Error compilando archivos Java");
        }
    }
    
    private static void showServerDetails() {
        System.out.println("=== DETALLES DEL SISTEMA ===");
        System.out.println("Servidor Web Principal:");
        System.out.println("  - Puerto: 8080");
        System.out.println("  - Función: Interfaz web y coordinación");
        System.out.println();
        System.out.println("Servidores de Procesamiento:");
        System.out.println("  - Servidor 1: Puerto 8081 (procesa 1/3 de los libros)");
        System.out.println("  - Servidor 2: Puerto 8082 (procesa 1/3 de los libros)");
        System.out.println("  - Servidor 3: Puerto 8083 (procesa 1/3 de los libros)");
        System.out.println();
        System.out.println("Todos los servidores trabajan en paralelo para");
        System.out.println("distribuir la carga de procesamiento de texto.");
    }
    
    private static void stopServers() {
        if (!serversRunning) {
            System.out.println("Los servidores no están ejecutándose.");
            return;
        }
        
        System.out.println("\nDeteniendo servidores...");
        
        // Detener servidor web
        if (webServerProcess != null && webServerProcess.isAlive()) {
            webServerProcess.destroy();
            try {
                webServerProcess.waitFor(5, TimeUnit.SECONDS);
                if (webServerProcess.isAlive()) {
                    webServerProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("- Servidor web detenido");
        }
        
        // Detener servidores de procesamiento
        for (int i = 0; i < 3; i++) {
            if (processingServerProcesses[i] != null && processingServerProcesses[i].isAlive()) {
                processingServerProcesses[i].destroy();
                try {
                    processingServerProcesses[i].waitFor(5, TimeUnit.SECONDS);
                    if (processingServerProcesses[i].isAlive()) {
                        processingServerProcesses[i].destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("- Servidor de procesamiento " + (i + 1) + " detenido");
            }
        }
        
        serversRunning = false;
        System.out.println("✅ Todos los servidores han sido detenidos");
    }
    
    private static void showServerStatus() {
        System.out.println("\n=== ESTADO DE SERVIDORES ===");
        
        if (!serversRunning) {
            System.out.println("❌ Sistema detenido");
            return;
        }
        
        System.out.println("✅ Sistema en ejecución");
        System.out.println();
        
        // Estado del servidor web
        if (webServerProcess != null && webServerProcess.isAlive()) {
            System.out.println("✅ Servidor Web (puerto 8080): ACTIVO");
        } else {
            System.out.println("❌ Servidor Web (puerto 8080): INACTIVO");
        }
        
        // Estado de servidores de procesamiento
        for (int i = 0; i < 3; i++) {
            if (processingServerProcesses[i] != null && processingServerProcesses[i].isAlive()) {
                System.out.println("✅ Servidor Procesamiento " + (i + 1) + " (puerto " + (8081 + i) + "): ACTIVO");
            } else {
                System.out.println("❌ Servidor Procesamiento " + (i + 1) + " (puerto " + (8081 + i) + "): INACTIVO");
            }
        }
        
        if (serversRunning) {
            System.out.println("\n🌐 Interfaz web disponible en: http://localhost:8080");
        }
    }
    
    private static void showHelp() {
        System.out.println("\n=== AYUDA - SISTEMA DISTRIBUIDO ===");
        System.out.println();
        System.out.println("DESCRIPCIÓN:");
        System.out.println("Este sistema distribuido busca frases idénticas de n palabras");
        System.out.println("entre diferentes libros usando 3 servidores de procesamiento");
        System.out.println("que trabajan en paralelo.");
        System.out.println();
        System.out.println("ARQUITECTURA:");
        System.out.println("- 1 Servidor Web (puerto 8080): Interfaz de usuario y coordinador");
        System.out.println("- 3 Servidores de Procesamiento (puertos 8081-8083): Análisis de texto");
        System.out.println();
        System.out.println("FUNCIONAMIENTO:");
        System.out.println("1. Los libros se distribuyen equitativamente entre los 3 servidores");
        System.out.println("2. Cada servidor procesa su conjunto de libros en paralelo");
        System.out.println("3. Se extraen n-gramas (frases de n palabras) de cada libro");
        System.out.println("4. Se comparan los n-gramas entre libros para encontrar coincidencias");
        System.out.println("5. Los resultados se consolidan y presentan al usuario");
        System.out.println();
        System.out.println("USO:");
        System.out.println("1. Inicia el sistema con la opción 1");
        System.out.println("2. Abre http://localhost:8080 en tu navegador");
        System.out.println("3. Introduce el número de palabras a buscar (ej: 4)");
        System.out.println("4. Haz clic en 'Buscar Frases Coincidentes'");
        System.out.println("5. Observa los resultados en tiempo real");
        System.out.println();
        System.out.println("TECNOLOGÍAS:");
        System.out.println("- Java con HttpServer para comunicación HTTP");
        System.out.println("- Procesamiento concurrente con ExecutorService");
        System.out.println("- Distribución de carga entre múltiples servidores");
    }
}
