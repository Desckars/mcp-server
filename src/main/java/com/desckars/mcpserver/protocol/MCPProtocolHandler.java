package com.desckars.mcpserver.protocol;

import com.desckars.mcpserver.handlers.ToolManager;
import com.desckars.mcpserver.handlers.ResourceManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Manejador del protocolo MCP (Model Context Protocol) Universal
 * 
 * Implementa el protocolo JSON-RPC para MCP, manejando los mensajes
 * de inicialización, herramientas, recursos y notificaciones para
 * múltiples LLMs y servicios.
 */
public class MCPProtocolHandler {

    private static final Logger logger = LoggerFactory.getLogger(MCPProtocolHandler.class);
    private static final String MCP_VERSION = "2024-11-05";
    
    private final ObjectMapper objectMapper;
    private final ToolManager toolManager;
    private final ResourceManager resourceManager;
    
    // Información del servidor
    private String serverName = "universal-mcp-server-java";
    private String serverVersion = "1.0.0";
    private boolean supportsTools = true;
    private boolean supportsResources = true;
    private boolean supportsPrompts = false;
    private boolean supportsLogging = false;
    
    private boolean isInitialized = false;

    public MCPProtocolHandler(ToolManager toolManager, ResourceManager resourceManager) {
        this.objectMapper = new ObjectMapper();
        this.toolManager = toolManager;
        this.resourceManager = resourceManager;
        
        logger.debug("MCP Protocol Handler inicializado con soporte universal");
    }

    /**
     * Configura la información del servidor universal
     */
    public void setServerInfo(String name, String version, boolean tools, 
                             boolean resources, boolean prompts, boolean logging) {
        this.serverName = name;
        this.serverVersion = version;
        this.supportsTools = tools;
        this.supportsResources = resources;
        this.supportsPrompts = prompts;
        this.supportsLogging = logging;
        
        logger.info("🔧 Configuración del servidor: {} v{}", name, version);
    }

    /**
     * Maneja un mensaje JSON-RPC entrante de cualquier cliente MCP
     */
    public CompletableFuture<String> handleMessage(String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("📨 Procesando mensaje MCP: {}", 
                           message.length() > 200 ? message.substring(0, 200) + "..." : message);
                
                JsonNode messageNode = objectMapper.readTree(message);
                
                // Verificar que es un mensaje JSON-RPC válido
                if (!messageNode.has("jsonrpc") || !"2.0".equals(messageNode.get("jsonrpc").asText())) {
                    return createErrorResponse(null, -32600, "Invalid Request", "Mensaje JSON-RPC inválido");
                }
                
                String method = messageNode.path("method").asText();
                JsonNode id = messageNode.get("id");
                JsonNode params = messageNode.get("params");
                
                logger.debug("🎯 Método MCP: {}", method);
                
                // Manejar diferentes métodos MCP
                switch (method) {
                    case "initialize":
                        return handleInitialize(id, params);
                    case "initialized":
                        return handleInitialized(id);
                    case "tools/list":
                        return handleToolsList(id);
                    case "tools/call":
                        return handleToolsCall(id, params);
                    case "resources/list":
                        return handleResourcesList(id);
                    case "resources/read":
                        return handleResourcesRead(id, params);
                    case "resources/updated":
                        return handleResourcesUpdated(id, params);
                    case "ping":
                        return handlePing(id);
                    case "notifications/initialized":
                        return handleNotificationInitialized(id, params);
                    default:
                        logger.warn("⚠️ Método MCP no implementado: {}", method);
                        return createErrorResponse(id, -32601, "Method not found", 
                                                 "Método no implementado: " + method);
                }
                
            } catch (Exception e) {
                logger.error("❌ Error procesando mensaje MCP", e);
                return createErrorResponse(null, -32603, "Internal error", e.getMessage());
            }
        });
    }

    /**
     * Maneja el mensaje de inicialización del cliente MCP
     */
    private String handleInitialize(JsonNode id, JsonNode params) {
        try {
            logger.info("🔄 Recibida solicitud de inicialización MCP");
            
            // Extraer información del cliente si está disponible
            String clientName = params != null && params.has("clientInfo") 
                ? params.get("clientInfo").path("name").asText("unknown-client") 
                : "unknown-client";
            
            logger.info("👤 Cliente MCP: {}", clientName);
            
            // Crear respuesta de inicialización
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("protocolVersion", MCP_VERSION);
            
            // Información del servidor universal
            ObjectNode serverInfo = objectMapper.createObjectNode();
            serverInfo.put("name", serverName);
            serverInfo.put("version", serverVersion);
            result.set("serverInfo", serverInfo);
            
            // Capacidades del servidor universal
            ObjectNode capabilities = objectMapper.createObjectNode();
            
            if (supportsTools) {
                ObjectNode tools = objectMapper.createObjectNode();
                tools.put("listChanged", true);
                capabilities.set("tools", tools);
                logger.debug("🛠️ Herramientas habilitadas: {} disponibles", toolManager.getToolCount());
            }
            
            if (supportsResources) {
                ObjectNode resources = objectMapper.createObjectNode();
                resources.put("subscribe", false);
                resources.put("listChanged", true);
                capabilities.set("resources", resources);
                logger.debug("📁 Recursos habilitados: {} disponibles", resourceManager.getResourceCount());
            }
            
            if (supportsPrompts) {
                ObjectNode prompts = objectMapper.createObjectNode();
                prompts.put("listChanged", false);
                capabilities.set("prompts", prompts);
            }
            
            if (supportsLogging) {
                capabilities.set("logging", objectMapper.createObjectNode());
            }
            
            result.set("capabilities", capabilities);
            response.set("result", result);
            
            String responseStr = objectMapper.writeValueAsString(response);
            logger.debug("📤 Enviando respuesta de inicialización MCP");
            
            return responseStr;
            
        } catch (Exception e) {
            logger.error("❌ Error en inicialización MCP", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Maneja la notificación de inicialización completada
     */
    private String handleInitialized(JsonNode id) {
        logger.info("✅ Inicialización del cliente MCP completada");
        isInitialized = true;
        
        // Las notificaciones no requieren respuesta
        return null;
    }

    /**
     * Maneja la solicitud de lista de herramientas
     */
    private String handleToolsList(JsonNode id) {
        if (!isInitialized) {
            return createErrorResponse(id, -32002, "Server not initialized", 
                                     "Servidor no inicializado");
        }
        
        try {
            logger.debug("🔍 Obteniendo lista de herramientas universales");
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.set("tools", toolManager.getToolsListAsJson(objectMapper));
            
            response.set("result", result);
            
            logger.debug("📋 Enviando {} herramientas disponibles", toolManager.getToolCount());
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo lista de herramientas", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Maneja la llamada a una herramienta
     */
    private String handleToolsCall(JsonNode id, JsonNode params) {
        if (!isInitialized) {
            return createErrorResponse(id, -32002, "Server not initialized", 
                                     "Servidor no inicializado");
        }
        
        try {
            String toolName = params.path("name").asText();
            JsonNode arguments = params.get("arguments");
            
            logger.debug("⚡ Ejecutando herramienta: {} con argumentos: {}", toolName, arguments);
            
            String result = toolManager.executeTool(toolName, arguments, objectMapper);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            response.set("result", objectMapper.readTree(result));
            
            logger.debug("✅ Herramienta {} ejecutada exitosamente", toolName);
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Error ejecutando herramienta", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Maneja la solicitud de lista de recursos
     */
    private String handleResourcesList(JsonNode id) {
        if (!isInitialized) {
            return createErrorResponse(id, -32002, "Server not initialized", 
                                     "Servidor no inicializado");
        }
        
        try {
            logger.debug("🔍 Obteniendo lista de recursos universales");
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.set("resources", resourceManager.getResourcesListAsJson(objectMapper));
            
            response.set("result", result);
            
            logger.debug("📋 Enviando {} recursos disponibles", resourceManager.getResourceCount());
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Error obteniendo lista de recursos", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Maneja la lectura de un recurso específico
     */
    private String handleResourcesRead(JsonNode id, JsonNode params) {
        if (!isInitialized) {
            return createErrorResponse(id, -32002, "Server not initialized", 
                                     "Servidor no inicializado");
        }
        
        try {
            String uri = params.path("uri").asText();
            
            logger.debug("📖 Leyendo recurso: {}", uri);
            
            String result = resourceManager.readResource(uri, objectMapper);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            response.set("result", objectMapper.readTree(result));
            
            logger.debug("✅ Recurso {} leído exitosamente", uri);
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Error leyendo recurso", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Maneja notificación de recursos actualizados
     */
    private String handleResourcesUpdated(JsonNode id, JsonNode params) {
        logger.debug("🔄 Notificación de recursos actualizados recibida");
        // Las notificaciones no requieren respuesta
        return null;
    }

    /**
     * Maneja el ping para verificar conectividad
     */
    private String handlePing(JsonNode id) {
        try {
            logger.debug("🏓 Ping recibido");
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "ok");
            result.put("server", serverName);
            result.put("version", serverVersion);
            result.put("timestamp", System.currentTimeMillis());
            
            response.set("result", result);
            
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Error en ping", e);
            return createErrorResponse(id, -32603, "Internal error", e.getMessage());
        }
    }

    /**
     * Maneja notificación de inicialización
     */
    private String handleNotificationInitialized(JsonNode id, JsonNode params) {
        logger.info("📢 Notificación de inicialización recibida");
        // Las notificaciones no requieren respuesta
        return null;
    }

    /**
     * Crea una respuesta de error JSON-RPC
     */
    private String createErrorResponse(JsonNode id, int code, String message, String data) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            if (data != null) {
                error.put("data", data);
            }
            
            response.set("error", error);
            
            logger.warn("⚠️ Error MCP: {} - {}", code, message);
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Error creando respuesta de error", e);
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    /**
     * Verifica si el servidor está inicializado
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Obtiene el nombre del servidor
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Obtiene la versión del servidor
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Obtiene estadísticas del protocolo
     */
    public String getProtocolStats() {
        return String.format("Servidor: %s v%s, Inicializado: %s, Herramientas: %d, Recursos: %d",
                           serverName, serverVersion, isInitialized, 
                           toolManager.getToolCount(), resourceManager.getResourceCount());
    }
}