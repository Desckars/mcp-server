package com.desckars.mcpserver.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Transporte stdio para comunicación MCP Universal
 * 
 * Maneja la comunicación bidireccional a través de stdin/stdout
 * usando el protocolo JSON-RPC sobre stdio para múltiples tipos
 * de clientes MCP (Claude, OpenAI, etc.).
 */
public class StdioTransport {

    private static final Logger logger = LoggerFactory.getLogger(StdioTransport.class);
    
    private final ExecutorService executor;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    private Function<String, CompletableFuture<String>> messageHandler;
    private volatile boolean isRunning = false;
    private long startTime;

    public StdioTransport() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "mcp-transport-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            return thread;
        });
        
        // Configurar streams con encoding UTF-8
        try {
            this.reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            this.writer = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"), true);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error configurando encoding UTF-8", e);
        }
        
        logger.debug("🌐 Transporte stdio universal inicializado");
    }

    /**
     * Establece el manejador de mensajes MCP
     */
    public void setMessageHandler(Function<String, CompletableFuture<String>> handler) {
        if (handler == null) {
            throw new IllegalArgumentException("El manejador de mensajes no puede ser null");
        }
        this.messageHandler = handler;
        logger.debug("📡 Manejador de mensajes MCP configurado");
    }

    /**
     * Inicia el transporte de comunicación universal
     */
    public CompletableFuture<Void> start() {
        if (messageHandler == null) {
            throw new IllegalStateException("Manejador de mensajes no configurado");
        }
        
        logger.info("🚀 Iniciando transporte stdio universal...");
        isRunning = true;
        startTime = System.currentTimeMillis();
        
        return CompletableFuture.runAsync(this::runMessageLoop, executor);
    }

    /**
     * Detiene el transporte
     */
    public void shutdown() {
        logger.info("🛑 Deteniendo transporte stdio universal...");
        isRunning = false;
        
        try {
            executor.shutdown();
            
            // Estadísticas finales
            long uptime = System.currentTimeMillis() - startTime;
            logger.info("📊 Estadísticas finales: {} mensajes procesados, {} errores, {}ms uptime",
                       messageCount.get(), errorCount.get(), uptime);
                       
        } catch (Exception e) {
            logger.warn("⚠️ Error cerrando executor", e);
        }
    }

    /**
     * Bucle principal de procesamiento de mensajes universales
     */
    private void runMessageLoop() {
        logger.debug("🔄 Iniciando bucle de procesamiento de mensajes MCP");
        
        try {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    // Leer mensaje de entrada
                    String message = readMessage();
                    if (message == null) {
                        // EOF - cliente desconectado
                        logger.info("📡 Cliente MCP desconectado (EOF)");
                        break;
                    }
                    
                    if (message.trim().isEmpty()) {
                        continue;
                    }
                    
                    long msgId = messageCount.incrementAndGet();
                    logger.debug("📨 Mensaje #{} recibido ({} chars)", msgId, message.length());
                    
                    // Procesar mensaje de forma asíncrona
                    messageHandler.apply(message)
                        .thenAccept(response -> {
                            if (response != null && !response.isEmpty()) {
                                sendMessage(response, msgId);
                            }
                        })
                        .exceptionally(throwable -> {
                            errorCount.incrementAndGet();
                            logger.error("❌ Error procesando mensaje #{}", msgId, throwable);
                            sendErrorMessage(throwable.getMessage(), msgId);
                            return null;
                        });
                        
                } catch (IOException e) {
                    if (isRunning) {
                        logger.error("❌ Error leyendo mensaje MCP", e);
                        errorCount.incrementAndGet();
                        break;
                    }
                } catch (Exception e) {
                    logger.error("❌ Error inesperado en bucle de mensajes", e);
                    errorCount.incrementAndGet();
                }
            }
            
        } finally {
            logger.debug("🔚 Bucle de procesamiento terminado");
            isRunning = false;
        }
    }

    /**
     * Lee un mensaje desde stdin con soporte para múltiples formatos
     */
    private String readMessage() throws IOException {
        StringBuilder messageBuilder = new StringBuilder();
        String line;
        
        // Leer líneas hasta encontrar una línea vacía o EOF
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                // Línea vacía indica fin del mensaje
                if (messageBuilder.length() > 0) {
                    break;
                }
                continue;
            }
            
            // Agregar línea al mensaje
            if (messageBuilder.length() > 0) {
                messageBuilder.append("\n");
            }
            messageBuilder.append(line);
            
            // Para JSON-RPC, cada mensaje es típicamente una sola línea
            // o se puede determinar el final por estructura JSON válida
            if (isCompleteJsonMessage(messageBuilder.toString())) {
                break;
            }
        }
        
        return messageBuilder.length() > 0 ? messageBuilder.toString() : null;
    }

    /**
     * Verifica si el mensaje JSON está completo
     */
    private boolean isCompleteJsonMessage(String message) {
        try {
            message = message.trim();
            if (!message.startsWith("{") || !message.endsWith("}")) {
                return false;
            }
            
            int braceCount = 0;
            boolean inString = false;
            boolean escaped = false;
            
            for (char c : message.toCharArray()) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"' && !escaped) {
                    inString = !inString;
                    continue;
                }
                
                if (!inString) {
                    if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                    }
                }
            }
            
            return braceCount == 0;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Envía un mensaje a stdout
     */
    private synchronized void sendMessage(String message, long messageId) {
        try {
            logger.debug("📤 Enviando respuesta #{} ({} chars)", messageId, message.length());
            
            // Escribir mensaje seguido de nueva línea
            writer.println(message);
            writer.flush();
            
        } catch (Exception e) {
            logger.error("❌ Error enviando mensaje #{}", messageId, e);
            errorCount.incrementAndGet();
        }
    }

    /**
     * Envía un mensaje de error genérico
     */
    private synchronized void sendErrorMessage(String errorMessage, long messageId) {
        try {
            String errorResponse = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\",\"data\":\"%s\"}}",
                errorMessage.replace("\"", "\\\"")
            );
            
            logger.debug("📤 Enviando error #{}: {}", messageId, errorMessage);
            
            writer.println(errorResponse);
            writer.flush();
            
        } catch (Exception e) {
            logger.error("❌ Error enviando mensaje de error #{}", messageId, e);
        }
    }

    /**
     * Verifica si el transporte está activo
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Obtiene estadísticas del transporte
     */
    public String getTransportStats() {
        long uptime = isRunning ? System.currentTimeMillis() - startTime : 0;
        return String.format("Mensajes: %d, Errores: %d, Activo: %s, Uptime: %dms",
                           messageCount.get(), errorCount.get(), isRunning, uptime);
    }

    /**
     * Obtiene el número de mensajes procesados
     */
    public long getMessageCount() {
        return messageCount.get();
    }

    /**
     * Obtiene el número de errores
     */
    public long getErrorCount() {
        return errorCount.get();
    }
}