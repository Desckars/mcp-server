#!/usr/bin/env node

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ErrorCode,
  ListToolsRequestSchema,
  McpError,
} from '@modelcontextprotocol/sdk/types.js';
import dotenv from 'dotenv';
import { LLMManager } from './llm/LLMManager.js';
import { DatabaseManager } from './database/DatabaseManager.js';
import { Logger } from './utils/Logger.js';
import { ToolsRegistry } from './tools/ToolsRegistry.js';

// Cargar configuración de entorno
dotenv.config();

class UniversalMCPServer {
  constructor() {
    this.logger = new Logger();
    this.llmManager = new LLMManager();
    this.dbManager = new DatabaseManager();
    this.toolsRegistry = new ToolsRegistry(this.llmManager, this.dbManager);
    
    this.server = new Server(
      {
        name: 'universal-mcp-server',
        version: '1.0.0',
      },
      {
        capabilities: {
          tools: {},
          resources: {},
          prompts: {},
        },
      }
    );

    this.setupToolHandlers();
    this.setupErrorHandling();
  }

  async initialize() {
    try {
      this.logger.info('Inicializando servidor MCP Universal...');
      
      // Inicializar conexión a base de datos
      await this.dbManager.connect();
      this.logger.info('✅ Base de datos conectada');
      
      // Inicializar LLMs
      await this.llmManager.initialize();
      this.logger.info('✅ LLMs inicializados');
      
      // Registrar herramientas
      await this.toolsRegistry.registerTools();
      this.logger.info('✅ Herramientas registradas');
      
      this.logger.info('🚀 Servidor MCP Universal listo');
      
    } catch (error) {
      this.logger.error('Error durante la inicialización:', error);
      throw error;
    }
  }

  setupToolHandlers() {
    // Listar herramientas disponibles
    this.server.setRequestHandler(ListToolsRequestSchema, async () => {
      return {
        tools: this.toolsRegistry.getAvailableTools(),
      };
    });

    // Manejar llamadas a herramientas
    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;
      
      try {
        this.logger.info(`Ejecutando herramienta: ${name}`, { args });
        
        const result = await this.toolsRegistry.executeTool(name, args);
        
        this.logger.info(`Herramienta ${name} ejecutada exitosamente`);
        
        return {
          content: [
            {
              type: 'text',
              text: typeof result === 'string' ? result : JSON.stringify(result, null, 2),
            },
          ],
        };
        
      } catch (error) {
        this.logger.error(`Error ejecutando herramienta ${name}:`, error);
        
        throw new McpError(
          ErrorCode.InternalError,
          `Error ejecutando ${name}: ${error.message}`
        );
      }
    });
  }

  setupErrorHandling() {
    this.server.onerror = (error) => {
      this.logger.error('[MCP Server Error]', error);
    };

    // Manejo de señales del sistema
    process.on('SIGINT', async () => {
      this.logger.info('Recibida señal SIGINT. Cerrando servidor...');
      await this.shutdown();
      process.exit(0);
    });

    process.on('SIGTERM', async () => {
      this.logger.info('Recibida señal SIGTERM. Cerrando servidor...');
      await this.shutdown();
      process.exit(0);
    });

    // Manejo de errores no capturados
    process.on('uncaughtException', async (error) => {
      this.logger.error('Error no capturado:', error);
      await this.shutdown();
      process.exit(1);
    });

    process.on('unhandledRejection', async (reason, promise) => {
      this.logger.error('Promesa rechazada no manejada:', { reason, promise });
      await this.shutdown();
      process.exit(1);
    });
  }

  async shutdown() {
    try {
      this.logger.info('Cerrando servidor MCP Universal...');
      
      await this.server.close();
      await this.dbManager.disconnect();
      
      this.logger.info('✅ Servidor cerrado correctamente');
      
    } catch (error) {
      this.logger.error('Error durante el cierre:', error);
    }
  }

  async run() {
    try {
      await this.initialize();
      
      const transport = new StdioServerTransport();
      await this.server.connect(transport);
      
      this.logger.info('🌟 Servidor MCP Universal ejecutándose en stdio');
      
    } catch (error) {
      this.logger.error('Error al iniciar el servidor:', error);
      process.exit(1);
    }
  }
}

// Función principal
async function main() {
  const server = new UniversalMCPServer();
  await server.run();
}

// Ejecutar solo si es el módulo principal
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error('Error fatal:', error);
    process.exit(1);
  });
}

export { UniversalMCPServer };
