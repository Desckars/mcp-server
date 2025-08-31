package com.desckars.mcpserver.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestor universal de recursos MCP
 * 
 * Maneja el acceso a recursos como archivos, URLs, datos de bases de datos
 * y otros tipos de contenido disponible para múltiples clientes MCP.
 */
public class ResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    private final Map<String, MCPResource> resources = new ConcurrentHashMap<>();
    private final AtomicLong accessCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    private final Path basePath;
    private final int maxResourceSize = 10 * 1024 * 1024; // 10MB máximo

    /**
     * Interfaz para recursos MCP universales
     */
    public interface MCPResource {
        String getUri();
        String getName();
        String getDescription();
        String getMimeType();
        String getCategory();
        String readContent() throws Exception;
        
        /**
         * Indica si el recurso está disponible
         */
        default boolean isAvailable() { return true; }
        
        /**
         * Obtiene el tamaño estimado del recurso en bytes
         */
        default long getEstimatedSize() { return -1; }
        
        /**
         * Obtiene la versión del recurso
         */
        default String getVersion() { return "1.0.0"; }
        
        /**
         * Indica si el recurso requiere autenticación
         */
        default boolean requiresAuth() { return false; }
    }

    public ResourceManager() {
        // Configurar directorio base para recursos
        this.basePath = Paths.get(System.getProperty("user.dir")).resolve("resources");
        
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            logger.warn("⚠️ No se pudo crear directorio de recursos: {}", e.getMessage());
        }
        
        logger.debug("📁 ResourceManager inicializado con directorio base: {}", basePath);
    }

    /**
     * Registra un nuevo recurso universal
     */
    public void registerResource(MCPResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("El recurso no puede ser null");
        }
        
        String uri = resource.getUri();
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("El URI del recurso no puede estar vacío");
        }
        
        if (!resource.isAvailable()) {
            logger.debug("🚫 Recurso {} no está disponible, omitiendo registro", uri);
            return;
        }
        
        resources.put(uri, resource);
        logger.info("📄 Recurso registrado: {} ({})", uri, resource.getCategory());
    }

    /**
     * Desregistra un recurso
     */
    public void unregisterResource(String uri) {
        if (resources.remove(uri) != null) {
            logger.info("🗑️ Recurso desregistrado: {}", uri);
        }
    }

    /**
     * Obtiene la lista de recursos en formato JSON para MCP
     */
    public ArrayNode getResourcesListAsJson(ObjectMapper mapper) {
        ArrayNode resourcesArray = mapper.createArrayNode();
        
        for (MCPResource resource : resources.values()) {
            try {
                if (!resource.isAvailable()) {
                    continue;
                }
                
                ObjectNode resourceNode = mapper.createObjectNode();
                resourceNode.put("uri", resource.getUri());
                resourceNode.put("name", resource.getName());
                resourceNode.put("description", resource.getDescription());
                resourceNode.put("mimeType", resource.getMimeType());
                
                // Metadatos adicionales
                ObjectNode metadata = mapper.createObjectNode();
                metadata.put("category", resource.getCategory());
                metadata.put("version", resource.getVersion());
                metadata.put("requiresAuth", resource.requiresAuth());
                
                long size = resource.getEstimatedSize();
                if (size >= 0) {
                    metadata.put("estimatedSize", size);
                }
                
                resourceNode.set("_metadata", metadata);
                resourcesArray.add(resourceNode);
                
            } catch (Exception e) {
                logger.warn("⚠️ Error generando JSON para recurso {}: {}", 
                           resource.getUri(), e.getMessage());
            }
        }
        
        logger.debug("📋 Lista de recursos generada: {} recursos disponibles", 
                    resourcesArray.size());
        return resourcesArray;
    }

    /**
     * Lee un recurso específico
     */
    public String readResource(String uri, ObjectMapper mapper) throws Exception {
        long accessId = accessCount.incrementAndGet();
        logger.debug("📖 Acceso #{}: leyendo recurso {}", accessId, uri);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Buscar recurso registrado
            MCPResource resource = resources.get(uri);
            if (resource != null) {
                String result = readRegisteredResource(resource, mapper);
                
                long readTime = System.currentTimeMillis() - startTime;
                logger.debug("✅ Acceso #{}: recurso {} leído en {}ms", 
                            accessId, uri, readTime);
                            
                return result;
            }
            
            // Si no está registrado, intentar leer como archivo
            if (uri.startsWith("file://")) {
                String result = readFileResource(uri, mapper);
                
                long readTime = System.currentTimeMillis() - startTime;
                logger.debug("✅ Acceso #{}: archivo {} leído en {}ms", 
                            accessId, uri, readTime);
                            
                return result;
            }
            
            // Recurso no encontrado
            errorCount.incrementAndGet();
            return createErrorResponse(mapper, "Recurso no encontrado: " + uri);
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            logger.error("❌ Acceso #{}: error leyendo recurso {}", accessId, uri, e);
            return createErrorResponse(mapper, "Error leyendo recurso: " + e.getMessage());
        }
    }

    /**
     * Lee un recurso registrado
     */
    private String readRegisteredResource(MCPResource resource, ObjectMapper mapper) throws Exception {
        if (!resource.isAvailable()) {
            return createErrorResponse(mapper, "Recurso no disponible: " + resource.getUri());
        }
        
        if (resource.requiresAuth()) {
            return createErrorResponse(mapper, "Recurso requiere autenticación: " + resource.getUri());
        }
        
        try {
            String content = resource.readContent();
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentsArray = mapper.createArrayNode();
            
            ObjectNode contentObj = mapper.createObjectNode();
            contentObj.put("uri", resource.getUri());
            contentObj.put("mimeType", resource.getMimeType());
            
            if (resource.getMimeType().startsWith("text/") || 
                resource.getMimeType().contains("json") ||
                resource.getMimeType().contains("xml")) {
                contentObj.put("text", content);
            } else {
                // Para contenido binario, usar base64
                contentObj.put("blob", java.util.Base64.getEncoder().encodeToString(content.getBytes()));
            }
            
            contentsArray.add(contentObj);
            response.set("contents", contentsArray);
            
            return mapper.writeValueAsString(response);
            
        } catch (Exception e) {
            return createErrorResponse(mapper, "Error accediendo al recurso: " + e.getMessage());
        }
    }

    /**
     * Lee un recurso de archivo desde el sistema de archivos
     */
    private String readFileResource(String uri, ObjectMapper mapper) throws Exception {
        try {
            URI fileUri = URI.create(uri);
            Path filePath = Paths.get(fileUri.getPath());
            
            // Validar que el archivo esté en el directorio permitido
            Path resolvedPath = basePath.resolve(filePath.getFileName()).normalize();
            if (!resolvedPath.startsWith(basePath)) {
                return createErrorResponse(mapper, "Acceso al archivo no permitido");
            }
            
            if (!Files.exists(resolvedPath)) {
                return createErrorResponse(mapper, "Archivo no encontrado: " + filePath.getFileName());
            }
            
            // Verificar tamaño
            long size = Files.size(resolvedPath);
            if (size > maxResourceSize) {
                return createErrorResponse(mapper, 
                    String.format("Archivo demasiado grande: %d bytes (máximo: %d bytes)", 
                                 size, maxResourceSize));
            }
            
            String content = Files.readString(resolvedPath);
            String mimeType = Files.probeContentType(resolvedPath);
            if (mimeType == null) {
                mimeType = "text/plain";
            }
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentsArray = mapper.createArrayNode();
            
            ObjectNode contentObj = mapper.createObjectNode();
            contentObj.put("uri", uri);
            contentObj.put("mimeType", mimeType);
            contentObj.put("text", content);
            
            contentsArray.add(contentObj);
            response.set("contents", contentsArray);
            
            return mapper.writeValueAsString(response);
            
        } catch (Exception e) {
            return createErrorResponse(mapper, "Error leyendo archivo: " + e.getMessage());
        }
    }

    /**
     * Obtiene recursos por categoría
     */
    public Set<String> getResourcesByCategory(String category) {
        return resources.entrySet().stream()
                .filter(entry -> category.equals(entry.getValue().getCategory()))
                .filter(entry -> entry.getValue().isAvailable())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Obtiene todas las categorías disponibles
     */
    public Set<String> getAvailableCategories() {
        return resources.values().stream()
                .filter(MCPResource::isAvailable)
                .map(MCPResource::getCategory)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Verifica si un recurso está disponible
     */
    public boolean hasResource(String uri) {
        MCPResource resource = resources.get(uri);
        return resource != null && resource.isAvailable();
    }

    /**
     * Obtiene el número de recursos disponibles
     */
    public int getResourceCount() {
        return (int) resources.values().stream()
                .filter(MCPResource::isAvailable)
                .count();
    }

    /**
     * Obtiene estadísticas de acceso
     */
    public ObjectNode getAccessStats(ObjectMapper mapper) {
        ObjectNode stats = mapper.createObjectNode();
        stats.put("totalAccess", accessCount.get());
        stats.put("totalErrors", errorCount.get());
        stats.put("successRate", accessCount.get() > 0 
            ? (double)(accessCount.get() - errorCount.get()) / accessCount.get() 
            : 0.0);
        stats.put("availableResources", getResourceCount());
        stats.put("totalResources", resources.size());
        
        // Estadísticas por categoría
        ObjectNode categoryStats = mapper.createObjectNode();
        for (String category : getAvailableCategories()) {
            categoryStats.put(category, getResourcesByCategory(category).size());
        }
        stats.set("resourcesByCategory", categoryStats);
        
        return stats;
    }

    /**
     * Crea una respuesta de error
     */
    private String createErrorResponse(ObjectMapper mapper, String errorMessage) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode contentsArray = mapper.createArrayNode();
        
        ObjectNode errorContent = mapper.createObjectNode();
        errorContent.put("uri", "error://internal");
        errorContent.put("mimeType", "text/plain");
        errorContent.put("text", "❌ Error: " + errorMessage);
        
        contentsArray.add(errorContent);
        response.set("contents", contentsArray);
        
        return mapper.writeValueAsString(response);
    }

    /**
     * Limpia todos los recursos registrados
     */
    public void cleanup() {
        int count = resources.size();
        resources.clear();
        
        logger.info("🧹 Limpieza completada: {} recursos eliminados", count);
        logger.info("📊 Estadísticas finales: {} accesos, {} errores", 
                   accessCount.get(), errorCount.get());
    }

    /**
     * Obtiene información resumida del gestor
     */
    @Override
    public String toString() {
        return String.format("ResourceManager[recursos=%d, accesos=%d, errores=%d]",
                           getResourceCount(), accessCount.get(), errorCount.get());
    }
}