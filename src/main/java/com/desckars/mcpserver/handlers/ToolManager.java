package com.desckars.mcpserver.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestor universal de herramientas MCP
 * 
 * Se encarga de registrar, gestionar y ejecutar herramientas
 * disponibles para múltiples LLMs y servicios a través del protocolo MCP.
 */
public class ToolManager {

    private static final Logger logger = LoggerFactory.getLogger(ToolManager.class);
    
    private final Map<String, MCPTool> tools = new ConcurrentHashMap<>();
    private final AtomicLong executionCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Interfaz para herramientas MCP universales
     */
    public interface MCPTool {
        String getName();
        String getDescription();
        String getCategory();
        ObjectNode getInputSchema(ObjectMapper mapper);
        String execute(JsonNode arguments, ObjectMapper mapper) throws Exception;
        
        /**
         * Indica si la herramienta requiere autenticación especial
         */
        default boolean requiresAuth() { return false; }
        
        /**
         * Obtiene la versión de la herramienta
         */
        default String getVersion() { return "1.0.0"; }
        
        /**
         * Indica si la herramienta está habilitada
         */
        default boolean isEnabled() { return true; }
    }

    /**
     * Registra una nueva herramienta universal
     */
    public void registerTool(MCPTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("La herramienta no puede ser null");
        }
        
        String name = tool.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la herramienta no puede estar vacío");
        }
        
        if (!tool.isEnabled()) {
            logger.debug("🚫 Herramienta {} está deshabilitada, omitiendo registro", name);
            return;
        }
        
        tools.put(name, tool);
        logger.info("🔧 Herramienta registrada: {} ({})", name, tool.getCategory());
    }

    /**
     * Desregistra una herramienta
     */
    public void unregisterTool(String name) {
        if (tools.remove(name) != null) {
            logger.info("🗑️ Herramienta desregistrada: {}", name);
        }
    }

    /**
     * Obtiene la lista de herramientas en formato JSON para MCP
     */
    public ArrayNode getToolsListAsJson(ObjectMapper mapper) {
        ArrayNode toolsArray = mapper.createArrayNode();
        
        for (MCPTool tool : tools.values()) {
            try {
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.put("name", tool.getName());
                toolNode.put("description", tool.getDescription());
                
                // Schema de entrada
                ObjectNode inputSchema = tool.getInputSchema(mapper);
                toolNode.set("inputSchema", inputSchema);
                
                // Metadatos adicionales
                ObjectNode metadata = mapper.createObjectNode();
                metadata.put("category", tool.getCategory());
                metadata.put("version", tool.getVersion());
                metadata.put("requiresAuth", tool.requiresAuth());
                toolNode.set("_metadata", metadata);
                
                toolsArray.add(toolNode);
                
            } catch (Exception e) {
                logger.warn("⚠️ Error generando JSON para herramienta {}: {}", 
                           tool.getName(), e.getMessage());
            }
        }
        
        logger.debug("📋 Lista de herramientas generada: {} herramientas disponibles", tools.size());
        return toolsArray;
    }

    /**
     * Ejecuta una herramienta específica
     */
    public String executeTool(String toolName, JsonNode arguments, ObjectMapper mapper) throws Exception {
        long executionId = executionCount.incrementAndGet();
        logger.debug("⚡ Ejecución #{}: herramienta '{}' con argumentos: {}", 
                    executionId, toolName, arguments);
        
        MCPTool tool = tools.get(toolName);
        if (tool == null) {
            errorCount.incrementAndGet();
            throw new IllegalArgumentException("Herramienta no encontrada: " + toolName);
        }
        
        if (!tool.isEnabled()) {
            errorCount.incrementAndGet();
            throw new IllegalStateException("Herramienta deshabilitada: " + toolName);
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            String result = tool.execute(arguments, mapper);
            
            long executionTime = System.currentTimeMillis() - startTime;
            logger.debug("✅ Ejecución #{}: herramienta '{}' completada en {}ms", 
                        executionId, toolName, executionTime);
            
            return result;
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            logger.error("❌ Ejecución #{}: error en herramienta '{}'", executionId, toolName, e);
            throw new RuntimeException("Error ejecutando herramienta " + toolName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta una herramienta de forma asíncrona
     */
    public java.util.concurrent.CompletableFuture<String> executeToolAsync(String toolName, JsonNode arguments, ObjectMapper mapper) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return executeTool(toolName, arguments, mapper);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Verifica si una herramienta está registrada y habilitada
     */
    public boolean hasToolEnabled(String name) {
        MCPTool tool = tools.get(name);
        return tool != null && tool.isEnabled();
    }

    /**
     * Obtiene información detallada de una herramienta
     */
    public ObjectNode getToolInfo(String toolName, ObjectMapper mapper) {
        MCPTool tool = tools.get(toolName);
        if (tool == null) {
            return null;
        }
        
        ObjectNode info = mapper.createObjectNode();
        info.put("name", tool.getName());
        info.put("description", tool.getDescription());
        info.put("category", tool.getCategory());
        info.put("version", tool.getVersion());
        info.put("enabled", tool.isEnabled());
        info.put("requiresAuth", tool.requiresAuth());
        info.set("inputSchema", tool.getInputSchema(mapper));
        
        return info;
    }

    /**
     * Obtiene herramientas por categoría
     */
    public Set<String> getToolsByCategory(String category) {
        return tools.entrySet().stream()
                .filter(entry -> category.equals(entry.getValue().getCategory()))
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Obtiene todas las categorías disponibles
     */
    public Set<String> getAvailableCategories() {
        return tools.values().stream()
                .filter(MCPTool::isEnabled)
                .map(MCPTool::getCategory)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Obtiene el número de herramientas registradas y habilitadas
     */
    public int getToolCount() {
        return (int) tools.values().stream()
                .filter(MCPTool::isEnabled)
                .count();
    }

    /**
     * Obtiene el número total de herramientas (incluidas las deshabilitadas)
     */
    public int getTotalToolCount() {
        return tools.size();
    }

    /**
     * Obtiene estadísticas de ejecución
     */
    public ObjectNode getExecutionStats(ObjectMapper mapper) {
        ObjectNode stats = mapper.createObjectNode();
        stats.put("totalExecutions", executionCount.get());
        stats.put("totalErrors", errorCount.get());
        stats.put("successRate", executionCount.get() > 0 
            ? (double)(executionCount.get() - errorCount.get()) / executionCount.get() 
            : 0.0);
        stats.put("enabledTools", getToolCount());
        stats.put("totalTools", getTotalToolCount());
        
        // Estadísticas por categoría
        ObjectNode categoryStats = mapper.createObjectNode();
        for (String category : getAvailableCategories()) {
            categoryStats.put(category, getToolsByCategory(category).size());
        }
        stats.set("toolsByCategory", categoryStats);
        
        return stats;
    }

    /**
     * Habilita o deshabilita una herramienta dinámicamente (si la implementación lo soporta)
     */
    public boolean toggleToolStatus(String toolName, boolean enabled) {
        MCPTool tool = tools.get(toolName);
        if (tool == null) {
            return false;
        }
        
        logger.info("🔄 Cambiando estado de herramienta '{}': {}", 
                   toolName, enabled ? "habilitada" : "deshabilitada");
        
        // Nota: esto requiere que la implementación de MCPTool soporte cambio de estado dinámico
        // Por ahora, solo loggeamos el cambio
        return true;
    }

    /**
     * Limpia todas las herramientas registradas
     */
    public void cleanup() {
        int count = tools.size();
        tools.clear();
        
        logger.info("🧹 Limpieza completada: {} herramientas eliminadas", count);
        logger.info("📊 Estadísticas finales: {} ejecuciones, {} errores", 
                   executionCount.get(), errorCount.get());
    }

    /**
     * Obtiene información resumida del gestor
     */
    @Override
    public String toString() {
        return String.format("ToolManager[herramientas=%d, ejecuciones=%d, errores=%d]",
                           getToolCount(), executionCount.get(), errorCount.get());
    }
}