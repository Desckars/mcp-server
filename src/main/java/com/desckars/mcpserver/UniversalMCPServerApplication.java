package com.desckars.mcpserver;

import com.desckars.mcpserver.config.MCPConfig;
import com.desckars.mcpserver.handlers.ToolManager;
import com.desckars.mcpserver.handlers.ResourceManager;
import com.desckars.mcpserver.protocol.MCPProtocolHandler;
import com.desckars.mcpserver.transport.StdioTransport;
import com.desckars.mcpserver.tools.*;
import com.desckars.mcpserver.database.DatabaseService;
import com.desckars.mcpserver.llm.LLMService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.concurrent.CompletableFuture;

/**
 * Aplicación principal del Servidor MCP Universal Java
 * 
 * Servidor MCP universal compatible con múltiples LLMs (GPT, Gemini, Llama)
 * y bases de datos, implementado con Spring Boot y el protocolo MCP 2024-11-05.
 * 
 * Características:
 * - Soporte para múltiples LLMs (OpenAI, Google Gemini, Hugging Face)
 * - Integración con MongoDB
 * - Herramientas extensibles (calculadora, filesystem, database, llm)
 * - Protocolo JSON-RPC sobre stdio
 * - Arquitectura modular y escalable
 * 
 * @author Desckars
 * @version 1.0.0
 */
@SpringBootApplication
@ComponentScan(basePackages = \"com.desckars.mcpserver\")
public class UniversalMCPServerApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(UniversalMCPServerApplication.class);
    
    @Autowired
    private MCPConfig mcpConfig;
    
    @Autowired
    private DatabaseService databaseService;
    
    @Autowired
    private LLMService llmService;
    
    private MCPProtocolHandler protocolHandler;
    private StdioTransport transport;
    private ToolManager toolManager;
    private ResourceManager resourceManager;

    public static void main(String[] args) {
        // Configurar para ejecutar sin interfaz gráfica
        System.setProperty(\"java.awt.headless\", \"true\");
        
        SpringApplication app = new SpringApplication(UniversalMCPServerApplication.class);
        app.setLogStartupInfo(false); // Reducir logs de inicio para stdio
        app.run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info(\"🚀 Iniciando Universal MCP Server Java v1.0.0\");
        logger.info(\"📋 Configuración: LLMs={}, Database={}\", 
                   mcpConfig.getLlm().getEnabledProviders(),
                   mcpConfig.getDatabase().isEnabled());
        
        try {
            // Inicializar componentes
            initializeComponents();
            
            // Configurar servidor MCP
            setupMcpServer();
            
            // Iniciar servidor
            startServer();
            
        } catch (Exception e) {
            logger.error(\"❌ Error fatal al iniciar el servidor MCP\", e);
            System.exit(1);
        }
    }

    /**
     * Inicializa todos los componentes del servidor
     */
    private void initializeComponents() {
        logger.debug(\"🔧 Inicializando componentes del servidor...\");
        
        // Inicializar transporte stdio
        transport = new StdioTransport();
        
        // Inicializar manejadores
        toolManager = new ToolManager();
        resourceManager = new ResourceManager();
        
        // Registrar herramientas universales
        registerUniversalTools();
        
        // Registrar recursos
        registerUniversalResources();
        
        logger.debug(\"✅ Componentes inicializados correctamente\");
    }

    /**
     * Registra las herramientas universales del servidor
     */
    private void registerUniversalTools() {
        logger.debug(\"🛠️  Registrando herramientas universales...\");
        
        // Herramienta de calculadora
        CalculatorTool calculator = new CalculatorTool();
        toolManager.registerTool(calculator);
        
        // Herramienta de sistema de archivos
        FileSystemTool fileSystem = new FileSystemTool();
        toolManager.registerTool(fileSystem);
        
        // Herramienta de base de datos (si está habilitada)
        if (mcpConfig.getDatabase().isEnabled()) {
            DatabaseTool databaseTool = new DatabaseTool(databaseService);
            toolManager.registerTool(databaseTool);
        }
        
        // Herramientas de LLM (si están habilitadas)
        if (mcpConfig.getLlm().isEnabled()) {
            // OpenAI
            if (mcpConfig.getLlm().getOpenai().isEnabled()) {
                OpenAITool openaiTool = new OpenAITool(llmService);
                toolManager.registerTool(openaiTool);
            }
            
            // Google Gemini
            if (mcpConfig.getLlm().getGemini().isEnabled()) {
                GeminiTool geminiTool = new GeminiTool(llmService);
                toolManager.registerTool(geminiTool);
            }
            
            // Hugging Face
            if (mcpConfig.getLlm().getHuggingface().isEnabled()) {
                HuggingFaceTool huggingfaceTool = new HuggingFaceTool(llmService);
                toolManager.registerTool(huggingfaceTool);
            }
        }
        
        logger.info(\"✅ Registradas {} herramientas universales\", toolManager.getToolCount());
    }

    /**
     * Registra los recursos universales del servidor
     */
    private void registerUniversalResources() {
        logger.debug(\"📁 Registrando recursos universales...\");
        
        // Recurso de información del servidor
        resourceManager.registerResource(new ServerInfoResource(mcpConfig));
        
        // Recurso de estado de la base de datos
        if (mcpConfig.getDatabase().isEnabled()) {
            resourceManager.registerResource(new DatabaseStatusResource(databaseService));
        }
        
        // Recursos de estado de LLMs
        if (mcpConfig.getLlm().isEnabled()) {
            resourceManager.registerResource(new LLMStatusResource(llmService));
        }
        
        logger.info(\"✅ Registrados {} recursos universales\", resourceManager.getResourceCount());
    }

    /**
     * Configura el manejador de protocolo MCP
     */
    private void setupMcpServer() {
        logger.debug(\"🌐 Configurando protocolo MCP...\");
        
        protocolHandler = new MCPProtocolHandler(toolManager, resourceManager);
        transport.setMessageHandler(protocolHandler::handleMessage);
        
        // Configurar información del servidor
        protocolHandler.setServerInfo(
            \"universal-mcp-server-java\",
            \"1.0.0\",
            true,  // soporta tools
            true,  // soporta resources  
            false, // no soporta prompts
            false  // no soporta logging
        );
        
        logger.debug(\"✅ Protocolo MCP configurado\");
    }

    /**
     * Inicia el servidor MCP
     */
    private void startServer() {
        logger.info(\"🌟 Iniciando servidor MCP en modo stdio...\");
        
        // Configurar shutdown hook para limpieza
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        try {
            // Iniciar transporte de comunicación
            CompletableFuture<Void> serverFuture = transport.start();
            
            logger.info(\"✅ Servidor MCP Universal iniciado y listo para recibir conexiones\");
            logger.info(\"📊 Estado: {} herramientas, {} recursos disponibles\", 
                       toolManager.getToolCount(), resourceManager.getResourceCount());
            
            // Esperar hasta que el servidor termine
            serverFuture.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn(\"⚠️  Servidor interrumpido\");
        } catch (Exception e) {
            logger.error(\"❌ Error durante la ejecución del servidor\", e);
            throw new RuntimeException(\"Error en el servidor MCP Universal\", e);
        }
    }

    /**
     * Limpia recursos al cerrar el servidor
     */
    private void shutdown() {
        logger.info(\"🔄 Cerrando Servidor MCP Universal...\");
        
        try {
            if (transport != null) {
                transport.shutdown();
            }
            
            if (toolManager != null) {
                toolManager.cleanup();
            }
            
            if (resourceManager != null) {
                resourceManager.cleanup();
            }
            
            if (databaseService != null) {
                databaseService.disconnect();
            }
            
            if (llmService != null) {
                llmService.cleanup();
            }
            
            logger.info(\"✅ Servidor MCP Universal cerrado correctamente\");
            
        } catch (Exception e) {
            logger.error(\"❌ Error durante el cierre del servidor\", e);
        }
    }
}