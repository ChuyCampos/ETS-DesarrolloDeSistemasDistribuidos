import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class ProcessingServer {
    private int port;
    private int serverId;
    private List<String> assignedBooks;
    private Map<String, List<String>> bookContent;
    private static final String BOOKS_DIRECTORY = "LIBROS_TXT";
    private ExecutorService executorService;
    
    public ProcessingServer(int port, int serverId) {
        this.port = port;
        this.serverId = serverId;
        this.assignedBooks = new ArrayList<>();
        this.bookContent = new HashMap<>();
        this.executorService = Executors.newFixedThreadPool(5);
        loadAssignedBooks();
    }
    
    private void loadAssignedBooks() {
        try {
            Path booksPath = Paths.get(BOOKS_DIRECTORY);
            List<Path> allBooks = new ArrayList<>();
            
            Files.walk(booksPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(allBooks::add);
            
            int totalBooks = allBooks.size();
            int booksPerServer = totalBooks / 3;
            int startIndex = (serverId - 1) * booksPerServer;
            int endIndex = serverId == 3 ? totalBooks : startIndex + booksPerServer;
            
            System.out.println("Servidor " + serverId + " inicializándose...");
            System.out.println("Libros asignados: " + (endIndex - startIndex) + " de " + totalBooks);
            
            for (int i = startIndex; i < endIndex; i++) {
                Path bookPath = allBooks.get(i);
                String bookName = bookPath.getFileName().toString();
                assignedBooks.add(bookName);
                loadBookContent(bookPath, bookName);
            }
            
            System.out.println("Servidor " + serverId + " listo. Libros cargados: " + assignedBooks.size());
            
        } catch (IOException e) {
            System.err.println("Error cargando libros en servidor " + serverId + ": " + e.getMessage());
        }
    }
    
    private void loadBookContent(Path bookPath, String bookName) {
        try {
            List<String> lines = Files.readAllLines(bookPath);
            List<String> cleanedLines = new ArrayList<>();
            
            for (String line : lines) {
                String cleaned = cleanText(line);
                if (!cleaned.isEmpty()) {
                    cleanedLines.add(cleaned);
                }
            }
            
            bookContent.put(bookName, cleanedLines);
            
        } catch (IOException e) {
            System.err.println("Error leyendo libro " + bookName + ": " + e.getMessage());
        }
    }
    
    private String cleanText(String text) {
        return text.replaceAll("[^\\p{L}\\s]", " ")
                   .replaceAll("\\s+", " ")
                   .trim()
                   .toLowerCase();
    }
    
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/process", new ProcessHandler());
        server.setExecutor(executorService);
        server.start();
        System.out.println("Servidor de procesamiento " + serverId + " iniciado en puerto " + port);
    }
    
    private class ProcessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String[] params = requestBody.split("=");
                int nWords = Integer.parseInt(params[1]);
                
                System.out.println("Servidor " + serverId + " procesando búsqueda de " + nWords + " palabras...");
                
                String results = processNGramSearch(nWords);
                
                exchange.sendResponseHeaders(200, results.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(results.getBytes());
                }
            }
        }
        
        private String processNGramSearch(int nWords) {
            System.out.println("Servidor " + serverId + " extrayendo n-gramas de " + assignedBooks.size() + " libros...");
            
            Map<String, Set<String>> ngramsPerBook = new HashMap<>();
            
            for (String bookName : assignedBooks) {
                System.out.println("Servidor " + serverId + " procesando: " + bookName.substring(0, Math.min(30, bookName.length())) + "...");
                Set<String> ngrams = extractNGrams(bookName, nWords);
                ngramsPerBook.put(bookName, ngrams);
            }
            
            System.out.println("Servidor " + serverId + " buscando coincidencias...");
            return findMatches(ngramsPerBook);
        }
        
        private Set<String> extractNGrams(String bookName, int nWords) {
            Set<String> ngrams = new HashSet<>();
            List<String> lines = bookContent.get(bookName);
            
            if (lines == null) return ngrams;
            
            for (String line : lines) {
                String[] words = line.split("\\s+");
                
                for (int i = 0; i <= words.length - nWords; i++) {
                    StringBuilder ngram = new StringBuilder();
                    for (int j = 0; j < nWords; j++) {
                        if (j > 0) ngram.append(" ");
                        ngram.append(words[i + j]);
                    }
                    
                    String ngramStr = ngram.toString().trim();
                    if (ngramStr.length() > 0 && !ngramStr.matches(".*\\d.*")) {
                        ngrams.add(ngramStr);
                    }
                }
            }
            
            return ngrams;
        }
        
        private String findMatches(Map<String, Set<String>> ngramsPerBook) {
            StringBuilder results = new StringBuilder();
            List<String> books = new ArrayList<>(ngramsPerBook.keySet());
            
            int comparisons = 0;
            for (int i = 0; i < books.size(); i++) {
                for (int j = i + 1; j < books.size(); j++) {
                    String book1 = books.get(i);
                    String book2 = books.get(j);
                    
                    Set<String> ngrams1 = ngramsPerBook.get(book1);
                    Set<String> ngrams2 = ngramsPerBook.get(book2);
                    
                    Set<String> intersection = new HashSet<>(ngrams1);
                    intersection.retainAll(ngrams2);
                    
                    for (String commonNgram : intersection) {
                        if (commonNgram.trim().length() > 0) {
                            String shortBook1 = shortenBookName(book1);
                            String shortBook2 = shortenBookName(book2);
                            results.append("COINCIDENCIA: En ").append(shortBook1)
                                   .append(" y ").append(shortBook2)
                                   .append(" aparece: \"").append(commonNgram).append("\"\n");
                        }
                    }
                    comparisons++;
                }
            }
            
            System.out.println("Servidor " + serverId + " completó " + comparisons + " comparaciones");
            return results.toString();
        }
        
        private String shortenBookName(String fullName) {
            String name = fullName.replace("_.txt", "").replace("_", " ");
            String[] parts = name.split("__");
            if (parts.length >= 2) {
                String author = parts[0].replace("_", ", ");
                String title = parts[1].replace("_", " ");
                if (title.length() > 30) {
                    title = title.substring(0, 30) + "...";
                }
                return "\"" + title + "\" (" + author + ")";
            }
            return name.length() > 40 ? name.substring(0, 40) + "..." : name;
        }
    }
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java ProcessingServer <server_id>");
            System.out.println("server_id debe ser 1, 2 o 3");
            return;
        }
        
        int serverId = Integer.parseInt(args[0]);
        int port = 8080 + serverId;
        
        try {
            ProcessingServer server = new ProcessingServer(port, serverId);
            server.start();
            
            System.out.println("Servidor " + serverId + " listo para procesar solicitudes");
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Error en servidor " + serverId + ": " + e.getMessage());
        }
    }
}