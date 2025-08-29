package com.desckars.mcpserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.ArrayList;

/**
 * Configuración principal del Servidor MCP Universal
 * 
 * Esta clase centraliza toda la configuración del servidor,
 * incluyendo LLMs, base de datos, herramientas y transporte.
 */
@Configuration
@ConfigurationProperties(prefix = "mcp")
@Validated
public class MCPConfig {

    @Valid
    @NotNull
    private Server server = new Server();
    
    @Valid
    @NotNull
    private LLM llm = new LLM();
    
    @Valid
    @NotNull
    private Database database = new Database();
    
    @Valid
    @NotNull
    private Tools tools = new Tools();
    
    @Valid
    @NotNull
    private Transport transport = new Transport();
    
    @Valid
    @NotNull
    private Security security = new Security();

    // Getters y Setters
    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }
    
    public LLM getLlm() { return llm; }
    public void setLlm(LLM llm) { this.llm = llm; }
    
    public Database getDatabase() { return database; }
    public void setDatabase(Database database) { this.database = database; }
    
    public Tools getTools() { return tools; }
    public void setTools(Tools tools) { this.tools = tools; }
    
    public Transport getTransport() { return transport; }
    public void setTransport(Transport transport) { this.transport = transport; }
    
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    /**
     * Configuración del servidor
     */
    public static class Server {
        @NotBlank
        private String name = "universal-mcp-server-java";
        
        @NotBlank
        private String version = "1.0.0";
        
        @NotBlank
        private String protocolVersion = "2024-11-05";
        
        private boolean enableLogging = true;
        private int maxExecutionTime = 30000; // 30 segundos

        // Getters y Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
        
        public boolean isEnableLogging() { return enableLogging; }
        public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
        
        public int getMaxExecutionTime() { return maxExecutionTime; }
        public void setMaxExecutionTime(int maxExecutionTime) { this.maxExecutionTime = maxExecutionTime; }
    }

    /**
     * Configuración de LLMs
     */
    public static class LLM {
        private boolean enabled = true;
        private List<String> enabledProviders = new ArrayList<>();
        
        @Valid
        @NotNull
        private OpenAI openai = new OpenAI();
        
        @Valid
        @NotNull
        private Gemini gemini = new Gemini();
        
        @Valid
        @NotNull
        private HuggingFace huggingface = new HuggingFace();

        // Getters y Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public List<String> getEnabledProviders() { return enabledProviders; }
        public void setEnabledProviders(List<String> enabledProviders) { this.enabledProviders = enabledProviders; }
        
        public OpenAI getOpenai() { return openai; }
        public void setOpenai(OpenAI openai) { this.openai = openai; }
        
        public Gemini getGemini() { return gemini; }
        public void setGemini(Gemini gemini) { this.gemini = gemini; }
        
        public HuggingFace getHuggingface() { return huggingface; }
        public void setHuggingface(HuggingFace huggingface) { this.huggingface = huggingface; }

        public static class OpenAI {
            private boolean enabled = false;
            private String apiKey = "";
            private String baseUrl = "https://api.openai.com/v1";
            private String defaultModel = "gpt-3.5-turbo";
            private int maxTokens = 4096;
            private double temperature = 0.7;

            // Getters y Setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            
            public String getDefaultModel() { return defaultModel; }
            public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
            
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            
            public double getTemperature() { return temperature; }
            public void setTemperature(double temperature) { this.temperature = temperature; }
        }

        public static class Gemini {
            private boolean enabled = false;
            private String apiKey = "";
            private String baseUrl = "https://generativelanguage.googleapis.com/v1";
            private String defaultModel = "gemini-pro";
            private int maxTokens = 2048;
            private double temperature = 0.7;

            // Getters y Setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            
            public String getDefaultModel() { return defaultModel; }
            public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
            
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            
            public double getTemperature() { return temperature; }
            public void setTemperature(double temperature) { this.temperature = temperature; }
        }

        public static class HuggingFace {
            private boolean enabled = false;
            private String apiKey = "";
            private String baseUrl = "https://api-inference.huggingface.co";
            private String defaultModel = "microsoft/DialoGPT-medium";
            private int maxTokens = 1024;
            private double temperature = 0.7;

            // Getters y Setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            
            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            
            public String getDefaultModel() { return defaultModel; }
            public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
            
            public int getMaxTokens() { return maxTokens; }
            public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
            
            public double getTemperature() { return temperature; }
            public void setTemperature(double temperature) { this.temperature = temperature; }
        }
    }

    /**
     * Configuración de base de datos
     */
    public static class Database {
        private boolean enabled = false;
        private String type = "mongodb";
        private String connectionString = "mongodb://localhost:27017";
        private String databaseName = "mcp_server";
        private int connectionTimeout = 10000;
        private int maxPoolSize = 10;
        private boolean enableMetrics = true;

        // Getters y Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getConnectionString() { return connectionString; }
        public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        
        public boolean isEnableMetrics() { return enableMetrics; }
        public void setEnableMetrics(boolean enableMetrics) { this.enableMetrics = enableMetrics; }
    }

    /**
     * Configuración de herramientas
     */
    public static class Tools {
        private Calculator calculator = new Calculator();
        private FileSystem filesystem = new FileSystem();
        private Database database = new Database();
        private LLM llm = new LLM();

        // Getters y Setters
        public Calculator getCalculator() { return calculator; }
        public void setCalculator(Calculator calculator) { this.calculator = calculator; }
        
        public FileSystem getFilesystem() { return filesystem; }
        public void setFilesystem(FileSystem filesystem) { this.filesystem = filesystem; }
        
        public Database getDatabase() { return database; }
        public void setDatabase(Database database) { this.database = database; }
        
        public LLM getLlm() { return llm; }
        public void setLlm(LLM llm) { this.llm = llm; }

        public static class Calculator {
            private boolean enabled = true;
            private boolean enableAdvanced = true;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public boolean isEnableAdvanced() { return enableAdvanced; }
            public void setEnableAdvanced(boolean enableAdvanced) { this.enableAdvanced = enableAdvanced; }
        }

        public static class FileSystem {
            private boolean enabled = true;
            private String workspaceDir = "workspace";
            private int maxFileSize = 1024 * 1024; // 1MB
            private int maxListItems = 1000;
            private boolean allowWrite = true;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public String getWorkspaceDir() { return workspaceDir; }
            public void setWorkspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; }
            
            public int getMaxFileSize() { return maxFileSize; }
            public void setMaxFileSize(int maxFileSize) { this.maxFileSize = maxFileSize; }
            
            public int getMaxListItems() { return maxListItems; }
            public void setMaxListItems(int maxListItems) { this.maxListItems = maxListItems; }
            
            public boolean isAllowWrite() { return allowWrite; }
            public void setAllowWrite(boolean allowWrite) { this.allowWrite = allowWrite; }
        }

        public static class Database {
            private boolean enabled = true;
            private int maxResults = 100;
            private int queryTimeout = 30000;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public int getMaxResults() { return maxResults; }
            public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
            
            public int getQueryTimeout() { return queryTimeout; }
            public void setQueryTimeout(int queryTimeout) { this.queryTimeout = queryTimeout; }
        }

        public static class LLM {
            private boolean enabled = true;
            private int maxPromptLength = 4000;
            private int requestTimeout = 60000;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            
            public int getMaxPromptLength() { return maxPromptLength; }
            public void setMaxPromptLength(int maxPromptLength) { this.maxPromptLength = maxPromptLength; }
            
            public int getRequestTimeout() { return requestTimeout; }
            public void setRequestTimeout(int requestTimeout) { this.requestTimeout = requestTimeout; }
        }
    }

    /**
     * Configuración de transporte
     */
    public static class Transport {
        private String type = "stdio";
        private int timeout = 30000;
        private int bufferSize = 8192;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
        
        public int getBufferSize() { return bufferSize; }
        public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
    }

    /**
     * Configuración de seguridad
     */
    public static class Security {
        private boolean enabled = true;
        private boolean sandboxEnabled = true;
        private int maxExecutionTime = 10000;
        private List<String> allowedHosts = new ArrayList<>();
        private List<String> blockedCommands = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public boolean isSandboxEnabled() { return sandboxEnabled; }
        public void setSandboxEnabled(boolean sandboxEnabled) { this.sandboxEnabled = sandboxEnabled; }
        
        public int getMaxExecutionTime() { return maxExecutionTime; }
        public void setMaxExecutionTime(int maxExecutionTime) { this.maxExecutionTime = maxExecutionTime; }
        
        public List<String> getAllowedHosts() { return allowedHosts; }
        public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
        
        public List<String> getBlockedCommands() { return blockedCommands; }
        public void setBlockedCommands(List<String> blockedCommands) { this.blockedCommands = blockedCommands; }
    }
}