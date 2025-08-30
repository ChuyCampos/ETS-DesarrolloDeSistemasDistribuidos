import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class WebServer {
    private static final int PORT = 8080;
    private static final String[] SERVER_URLS = {
        "http://processing-server-1:8081",
        "http://processing-server-2:8082", 
        "http://processing-server-3:8083"
    };
    
    private ExecutorService executorService;
    
    public WebServer() {
        executorService = Executors.newFixedThreadPool(10);
    }
    
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new WebInterfaceHandler());
        server.createContext("/search", new SearchHandler());
        server.setExecutor(executorService);
        server.start();
        System.out.println("Servidor web iniciado en puerto " + PORT);
        System.out.println("Accede a http://localhost:" + PORT + " para usar la aplicación");
    }
    
    private static class WebInterfaceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String htmlContent = generateWebInterface();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, htmlContent.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(htmlContent.getBytes());
                }
            }
        }
        
        private String generateWebInterface() {
            return "<!DOCTYPE html>\n" +
                   "<html>\n" +
                   "<head>\n" +
                   "    <title>Búsqueda de Frases en Libros</title>\n" +
                   "    <meta charset='UTF-8'>\n" +
                   "    <style>\n" +
                   "        body { font-family: Arial, sans-serif; margin: 40px; background-color: #f5f5f5; }\n" +
                   "        .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n" +
                   "        h1 { color: #333; text-align: center; }\n" +
                   "        .form-group { margin: 20px 0; }\n" +
                   "        label { display: block; margin-bottom: 10px; font-weight: bold; }\n" +
                   "        input[type='number'] { width: 100px; padding: 8px; border: 1px solid #ddd; border-radius: 4px; }\n" +
                   "        button { background-color: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; }\n" +
                   "        button:hover { background-color: #0056b3; }\n" +
                   "        #results { margin-top: 30px; padding: 20px; background-color: #f8f9fa; border-radius: 5px; }\n" +
                   "        .loading { color: #007bff; font-style: italic; }\n" +
                   "        .result-item { margin: 10px 0; padding: 10px; background-color: white; border-left: 4px solid #007bff; }\n" +
                   "    </style>\n" +
                   "</head>\n" +
                   "<body>\n" +
                   "    <div class='container'>\n" +
                   "        <h1>Sistema Distribuido de Búsqueda de Frases</h1>\n" +
                   "        <p>Introduce el número de palabras consecutivas que quieres buscar en los libros:</p>\n" +
                   "        <div class='form-group'>\n" +
                   "            <label for='nWords'>Número de palabras:</label>\n" +
                   "            <input type='number' id='nWords' min='2' max='10' value='4'>\n" +
                   "            <button onclick='searchPhrases()'>Buscar Frases Coincidentes</button>\n" +
                   "        </div>\n" +
                   "        <div id='results'></div>\n" +
                   "    </div>\n" +
                   "    <script>\n" +
                   "        function searchPhrases() {\n" +
                   "            const nWords = document.getElementById('nWords').value;\n" +
                   "            const resultsDiv = document.getElementById('results');\n" +
                   "            \n" +
                   "            if (!nWords || nWords < 2) {\n" +
                   "                alert('Por favor introduce un número válido (mínimo 2)');\n" +
                   "                return;\n" +
                   "            }\n" +
                   "            \n" +
                   "            resultsDiv.innerHTML = '<div class=\"loading\">Procesando... Los servidores están analizando los libros en paralelo...</div>';\n" +
                   "            \n" +
                   "            fetch('/search', {\n" +
                   "                method: 'POST',\n" +
                   "                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n" +
                   "                body: 'nWords=' + nWords\n" +
                   "            })\n" +
                   "            .then(response => response.text())\n" +
                   "            .then(data => {\n" +
                   "                resultsDiv.innerHTML = '<h3>Resultados:</h3>' + data;\n" +
                   "            })\n" +
                   "            .catch(error => {\n" +
                   "                resultsDiv.innerHTML = '<div style=\"color: red;\">Error: ' + error.message + '</div>';\n" +
                   "            });\n" +
                   "        }\n" +
                   "    </script>\n" +
                   "</body>\n" +
                   "</html>";
        }
    }
    
    private static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String[] params = requestBody.split("=");
                int nWords = Integer.parseInt(params[1]);
                
                System.out.println("Nueva consulta recibida: buscar frases de " + nWords + " palabras");
                
                String results = processDistributedSearch(nWords);
                
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, results.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(results.getBytes());
                }
            }
        }
        
        private String processDistributedSearch(int nWords) {
            System.out.println("Iniciando búsqueda distribuida en 3 servidores...");
            
            List<Future<String>> futures = new ArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(3);
            
            for (int i = 0; i < SERVER_URLS.length; i++) {
                final int serverIndex = i;
                futures.add(executor.submit(() -> {
                    try {
                        return callProcessingServer(SERVER_URLS[serverIndex] + "/process", nWords);
                    } catch (IOException e) {
                        return "Error en servidor " + (serverIndex + 1) + ": " + e.getMessage();
                    }
                }));
            }
            
            StringBuilder combinedResults = new StringBuilder();
            Set<String> uniquePhrases = new HashSet<>();
            
            for (int i = 0; i < futures.size(); i++) {
                try {
                    String serverResult = futures.get(i).get(30, TimeUnit.SECONDS);
                    System.out.println("Resultado recibido del servidor " + (i + 1));
                    
                    String[] lines = serverResult.split("\n");
                    for (String line : lines) {
                        if (line.trim().startsWith("COINCIDENCIA:") && !uniquePhrases.contains(line)) {
                            uniquePhrases.add(line);
                            combinedResults.append("<div class='result-item'>").append(line.replace("COINCIDENCIA:", "")).append("</div>\n");
                        }
                    }
                } catch (Exception e) {
                    combinedResults.append("<div style='color: red;'>Error en servidor ").append(i + 1).append(": ").append(e.getMessage()).append("</div>\n");
                }
            }
            
            executor.shutdown();
            
            if (combinedResults.length() == 0) {
                return "<p>No se encontraron frases coincidentes de " + nWords + " palabras entre los libros.</p>";
            }
            
            return "<p>Se encontraron " + uniquePhrases.size() + " frases coincidentes:</p>\n" + combinedResults.toString();
        }
        
        private String callProcessingServer(String serverUrl, int nWords) throws IOException {
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            
            String postData = "nWords=" + nWords;
            try (OutputStream os = connection.getOutputStream()) {
                os.write(postData.getBytes());
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
            
            return response.toString();
        }
    }
    
    public static void main(String[] args) {
        try {
            WebServer server = new WebServer();
            server.start();
            
            System.out.println("\nPara detener el servidor, presiona Ctrl+C");
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Error iniciando el servidor: " + e.getMessage());
        }
    }
}