import { Logger } from '../utils/Logger.js';

export class ToolsRegistry {
  constructor(llmManager, databaseManager) {
    this.logger = new Logger('ToolsRegistry');
    this.llmManager = llmManager;
    this.databaseManager = databaseManager;
    this.tools = new Map();
  }

  async registerTools() {
    this.logger.info('Registrando herramientas...');

    // Herramientas básicas
    this.registerBasicTools();
    
    // Herramientas de LLM
    this.registerLLMTools();
    
    // Herramientas de base de datos
    this.registerDatabaseTools();
    
    // Herramientas de análisis
    this.registerAnalysisTools();

    this.logger.info(`✅ ${this.tools.size} herramientas registradas`);
  }

  registerBasicTools() {
    // Echo tool
    this.tools.set('echo', {
      name: 'echo',
      description: 'Repite el mensaje proporcionado',
      inputSchema: {
        type: 'object',
        properties: {
          message: {
            type: 'string',
            description: 'Mensaje a repetir',
          },
        },
        required: ['message'],
      },
      handler: async (args) => {
        return `Echo: ${args.message}`;
      },
    });

    // Get time tool
    this.tools.set('get_time', {
      name: 'get_time',
      description: 'Obtiene la hora actual',
      inputSchema: {
        type: 'object',
        properties: {},
      },
      handler: async () => {
        return `Hora actual: ${new Date().toISOString()}`;
      },
    });

    // System info tool
    this.tools.set('system_info', {
      name: 'system_info',
      description: 'Obtiene información del sistema y servidor MCP',
      inputSchema: {
        type: 'object',
        properties: {},
      },
      handler: async () => {
        const dbInfo = this.databaseManager.getConnectionInfo();
        const llmProviders = this.llmManager.getAvailableProviders();
        
        return {
          server: 'Universal MCP Server',
          version: '1.0.0',
          uptime: process.uptime(),
          nodeVersion: process.version,
          platform: process.platform,
          memory: process.memoryUsage(),
          database: dbInfo,
          llmProviders: llmProviders,
          toolsCount: this.tools.size,
        };
      },
    });
  }

  registerLLMTools() {
    // Generate text with LLM
    this.tools.set('llm_generate', {
      name: 'llm_generate',
      description: 'Genera texto usando un LLM específico',
      inputSchema: {
        type: 'object',
        properties: {
          prompt: {
            type: 'string',
            description: 'Texto prompt para generar',
          },
          provider: {
            type: 'string',
            description: 'Proveedor de LLM (openai, gemini, huggingface)',
            enum: ['openai', 'gemini', 'huggingface'],
          },
          temperature: {
            type: 'number',
            description: 'Temperatura para la generación (0.0-2.0)',
            minimum: 0.0,
            maximum: 2.0,
          },
          maxTokens: {
            type: 'number',
            description: 'Máximo número de tokens a generar',
            minimum: 1,
            maximum: 8000,
          },
        },
        required: ['prompt'],
      },
      handler: async (args) => {
        const provider = args.provider || 'openai';
        const options = {
          temperature: args.temperature || 0.7,
          maxTokens: args.maxTokens || 1000,
        };

        const startTime = Date.now();
        const result = await this.llmManager.generateText(args.prompt, provider, options);
        const duration = Date.now() - startTime;

        this.logger.logLLMRequest(provider, args.prompt, result, duration);

        return {
          provider,
          prompt: args.prompt,
          response: result,
          options,
          duration: `${duration}ms`,
        };
      },
    });

    // List available LLM providers
    this.tools.set('llm_providers', {
      name: 'llm_providers',
      description: 'Lista los proveedores de LLM disponibles',
      inputSchema: {
        type: 'object',
        properties: {},
      },
      handler: async () => {
        const providers = this.llmManager.getAvailableProviders();
        const providersInfo = {};

        for (const provider of providers) {
          providersInfo[provider] = this.llmManager.getProviderInfo(provider);
        }

        return {
          available: providers,
          details: providersInfo,
        };
      },
    });

    // Generate SQL/MongoDB query from natural language
    this.tools.set('generate_query', {
      name: 'generate_query',
      description: 'Genera una consulta MongoDB desde lenguaje natural',
      inputSchema: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'Consulta en lenguaje natural',
          },
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          provider: {
            type: 'string',
            description: 'Proveedor de LLM a usar',
            enum: ['openai', 'gemini', 'huggingface'],
          },
        },
        required: ['query', 'collection'],
      },
      handler: async (args) => {
        const schema = await this.databaseManager.getCollectionSchema(args.collection);
        const provider = args.provider || 'openai';

        const mongoQuery = await this.llmManager.generateSQLQuery(
          args.query,
          schema,
          provider
        );

        return {
          naturalLanguage: args.query,
          collection: args.collection,
          generatedQuery: mongoQuery,
          schema: schema,
          provider,
        };
      },
    });
  }

  registerDatabaseTools() {
    // List collections
    this.tools.set('db_list_collections', {
      name: 'db_list_collections',
      description: 'Lista todas las colecciones en la base de datos',
      inputSchema: {
        type: 'object',
        properties: {},
      },
      handler: async () => {
        const collections = await this.databaseManager.listCollections();
        return {
          collections,
          count: collections.length,
        };
      },
    });

    // Get collection schema
    this.tools.set('db_get_schema', {
      name: 'db_get_schema',
      description: 'Obtiene el esquema de una colección específica',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          sampleSize: {
            type: 'number',
            description: 'Tamaño de muestra para analizar',
            minimum: 1,
            maximum: 1000,
          },
        },
        required: ['collection'],
      },
      handler: async (args) => {
        const sampleSize = args.sampleSize || 100;
        const schema = await this.databaseManager.getCollectionSchema(args.collection, sampleSize);
        return schema;
      },
    });

    // Execute MongoDB query
    this.tools.set('db_find', {
      name: 'db_find',
      description: 'Ejecuta una consulta find en MongoDB',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          query: {
            type: 'object',
            description: 'Filtro de búsqueda MongoDB',
          },
          limit: {
            type: 'number',
            description: 'Límite de resultados',
            minimum: 1,
            maximum: 1000,
          },
          sort: {
            type: 'object',
            description: 'Criterio de ordenamiento',
          },
        },
        required: ['collection'],
      },
      handler: async (args) => {
        const options = {};
        if (args.limit) options.limit = args.limit;
        if (args.sort) options.sort = args.sort;

        const results = await this.databaseManager.find(
          args.collection,
          args.query || {},
          options
        );

        return {
          collection: args.collection,
          query: args.query,
          results,
          count: results.length,
        };
      },
    });

    // Execute aggregation
    this.tools.set('db_aggregate', {
      name: 'db_aggregate',
      description: 'Ejecuta una agregación en MongoDB',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          pipeline: {
            type: 'array',
            description: 'Pipeline de agregación MongoDB',
            items: {
              type: 'object',
            },
          },
        },
        required: ['collection', 'pipeline'],
      },
      handler: async (args) => {
        const results = await this.databaseManager.aggregate(
          args.collection,
          args.pipeline
        );

        return {
          collection: args.collection,
          pipeline: args.pipeline,
          results,
          count: results.length,
        };
      },
    });

    // Insert document
    this.tools.set('db_insert', {
      name: 'db_insert',
      description: 'Inserta un documento en una colección',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          document: {
            type: 'object',
            description: 'Documento a insertar',
          },
        },
        required: ['collection', 'document'],
      },
      handler: async (args) => {
        const result = await this.databaseManager.insertOne(
          args.collection,
          args.document
        );

        return {
          collection: args.collection,
          document: args.document,
          insertedId: result.insertedId,
          acknowledged: result.acknowledged,
        };
      },
    });

    // Update document
    this.tools.set('db_update', {
      name: 'db_update',
      description: 'Actualiza documentos en una colección',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          filter: {
            type: 'object',
            description: 'Filtro para encontrar documentos',
          },
          update: {
            type: 'object',
            description: 'Operaciones de actualización',
          },
          multiple: {
            type: 'boolean',
            description: 'Si actualizar múltiples documentos',
          },
        },
        required: ['collection', 'filter', 'update'],
      },
      handler: async (args) => {
        const result = args.multiple
          ? await this.databaseManager.updateMany(args.collection, args.filter, args.update)
          : await this.databaseManager.updateOne(args.collection, args.filter, args.update);

        return {
          collection: args.collection,
          filter: args.filter,
          update: args.update,
          multiple: args.multiple || false,
          modifiedCount: result.modifiedCount,
          matchedCount: result.matchedCount,
        };
      },
    });

    // Delete document
    this.tools.set('db_delete', {
      name: 'db_delete',
      description: 'Elimina documentos de una colección',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          filter: {
            type: 'object',
            description: 'Filtro para encontrar documentos a eliminar',
          },
          multiple: {
            type: 'boolean',
            description: 'Si eliminar múltiples documentos',
          },
        },
        required: ['collection', 'filter'],
      },
      handler: async (args) => {
        const result = args.multiple
          ? await this.databaseManager.deleteMany(args.collection, args.filter)
          : await this.databaseManager.deleteOne(args.collection, args.filter);

        return {
          collection: args.collection,
          filter: args.filter,
          multiple: args.multiple || false,
          deletedCount: result.deletedCount,
        };
      },
    });

    // Count documents
    this.tools.set('db_count', {
      name: 'db_count',
      description: 'Cuenta documentos en una colección',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          filter: {
            type: 'object',
            description: 'Filtro para contar documentos específicos',
          },
        },
        required: ['collection'],
      },
      handler: async (args) => {
        const count = await this.databaseManager.countDocuments(
          args.collection,
          args.filter || {}
        );

        return {
          collection: args.collection,
          filter: args.filter,
          count,
        };
      },
    });

    // Database stats
    this.tools.set('db_stats', {
      name: 'db_stats',
      description: 'Obtiene estadísticas de la base de datos',
      inputSchema: {
        type: 'object',
        properties: {},
      },
      handler: async () => {
        const stats = await this.databaseManager.getDatabaseStats();
        return stats;
      },
    });
  }

  registerAnalysisTools() {
    // Analyze data with LLM
    this.tools.set('analyze_data', {
      name: 'analyze_data',
      description: 'Analiza datos usando un LLM',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Colección a analizar',
          },
          query: {
            type: 'object',
            description: 'Filtro para obtener datos específicos',
          },
          question: {
            type: 'string',
            description: 'Pregunta específica sobre los datos',
          },
          provider: {
            type: 'string',
            description: 'Proveedor de LLM',
            enum: ['openai', 'gemini', 'huggingface'],
          },
          limit: {
            type: 'number',
            description: 'Límite de documentos a analizar',
            minimum: 1,
            maximum: 500,
          },
        },
        required: ['collection', 'question'],
      },
      handler: async (args) => {
        const limit = args.limit || 100;
        const provider = args.provider || 'openai';

        // Obtener datos
        const data = await this.databaseManager.find(
          args.collection,
          args.query || {},
          { limit }
        );

        if (data.length === 0) {
          return {
            collection: args.collection,
            question: args.question,
            analysis: 'No se encontraron datos para analizar.',
            dataCount: 0,
          };
        }

        // Analizar con LLM
        const analysis = await this.llmManager.analyzeData(
          data,
          args.question,
          provider
        );

        return {
          collection: args.collection,
          query: args.query,
          question: args.question,
          provider,
          dataCount: data.length,
          analysis,
        };
      },
    });

    // Smart query builder
    this.tools.set('smart_query', {
      name: 'smart_query',
      description: 'Construye y ejecuta consultas MongoDB inteligentemente usando LLM',
      inputSchema: {
        type: 'object',
        properties: {
          request: {
            type: 'string',
            description: 'Descripción en lenguaje natural de lo que quieres encontrar',
          },
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          provider: {
            type: 'string',
            description: 'Proveedor de LLM',
            enum: ['openai', 'gemini', 'huggingface'],
          },
          execute: {
            type: 'boolean',
            description: 'Si ejecutar la consulta generada automáticamente',
          },
        },
        required: ['request', 'collection'],
      },
      handler: async (args) => {
        const provider = args.provider || 'openai';
        const execute = args.execute !== false; // Por defecto true

        // Obtener esquema de la colección
        const schema = await this.databaseManager.getCollectionSchema(args.collection);

        // Generar consulta MongoDB
        const queryCode = await this.llmManager.generateSQLQuery(
          args.request,
          schema,
          provider
        );

        let results = null;
        let error = null;

        if (execute) {
          try {
            // Intentar ejecutar la consulta generada
            // Esto es simplificado - en un entorno real necesitarías un parser más robusto
            if (queryCode.includes('aggregate')) {
              // Es una agregación
              const pipelineMatch = queryCode.match(/aggregate\(\[(.*)\]\)/);
              if (pipelineMatch) {
                const pipelineStr = pipelineMatch[1];
                const pipeline = JSON.parse(`[${pipelineStr}]`);
                results = await this.databaseManager.aggregate(args.collection, pipeline);
              }
            } else if (queryCode.includes('find')) {
              // Es un find
              const queryMatch = queryCode.match(/find\((.*)\)/);
              if (queryMatch) {
                const queryStr = queryMatch[1];
                const query = queryStr ? JSON.parse(queryStr) : {};
                results = await this.databaseManager.find(args.collection, query, { limit: 100 });
              }
            } else if (queryCode.includes('countDocuments')) {
              // Es un count
              const queryMatch = queryCode.match(/countDocuments\((.*)\)/);
              if (queryMatch) {
                const queryStr = queryMatch[1];
                const query = queryStr ? JSON.parse(queryStr) : {};
                results = await this.databaseManager.countDocuments(args.collection, query);
              }
            }
          } catch (execError) {
            error = execError.message;
          }
        }

        return {
          request: args.request,
          collection: args.collection,
          provider,
          generatedQuery: queryCode,
          schema: schema,
          executed: execute,
          results,
          resultCount: Array.isArray(results) ? results.length : (typeof results === 'number' ? 1 : 0),
          error,
        };
      },
    });

    // Data insights
    this.tools.set('data_insights', {
      name: 'data_insights',
      description: 'Obtiene insights automáticos de una colección usando LLM',
      inputSchema: {
        type: 'object',
        properties: {
          collection: {
            type: 'string',
            description: 'Nombre de la colección',
          },
          provider: {
            type: 'string',
            description: 'Proveedor de LLM',
            enum: ['openai', 'gemini', 'huggingface'],
          },
          sampleSize: {
            type: 'number',
            description: 'Tamaño de muestra para análisis',
            minimum: 10,
            maximum: 500,
          },
        },
        required: ['collection'],
      },
      handler: async (args) => {
        const provider = args.provider || 'openai';
        const sampleSize = args.sampleSize || 100;

        // Obtener muestra de datos
        const data = await this.databaseManager.find(
          args.collection,
          {},
          { limit: sampleSize }
        );

        if (data.length === 0) {
          return {
            collection: args.collection,
            insights: 'No hay datos en la colección para analizar.',
            dataCount: 0,
          };
        }

        // Obtener estadísticas básicas
        const totalCount = await this.databaseManager.countDocuments(args.collection);
        const schema = await this.databaseManager.getCollectionSchema(args.collection);

        // Generar insights con LLM
        const analysisPrompt = `
Analiza estos datos y proporciona insights útiles:

COLECCIÓN: ${args.collection}
TOTAL DE DOCUMENTOS: ${totalCount}
MUESTRA ANALIZADA: ${data.length}
ESQUEMA: ${JSON.stringify(schema, null, 2)}

DATOS DE MUESTRA:
${JSON.stringify(data.slice(0, 10), null, 2)}

Proporciona insights sobre:
1. Patrones en los datos
2. Distribuciones interesantes
3. Posibles anomalías
4. Recomendaciones para consultas útiles
5. Calidad de los datos
`;

        const insights = await this.llmManager.generateText(
          analysisPrompt,
          provider,
          { temperature: 0.3 }
        );

        return {
          collection: args.collection,
          totalDocuments: totalCount,
          sampleSize: data.length,
          schema,
          insights,
          provider,
        };
      },
    });
  }

  getAvailableTools() {
    return Array.from(this.tools.values()).map(tool => ({
      name: tool.name,
      description: tool.description,
      inputSchema: tool.inputSchema,
    }));
  }

  async executeTool(name, args) {
    if (!this.tools.has(name)) {
      throw new Error(`Herramienta '${name}' no encontrada`);
    }

    const tool = this.tools.get(name);
    const startTime = Date.now();

    try {
      const result = await tool.handler(args);
      const duration = Date.now() - startTime;

      this.logger.logToolExecution(name, args, result, duration);
      return result;

    } catch (error) {
      const duration = Date.now() - startTime;
      this.logger.error(`Error ejecutando herramienta ${name}:`, error, {
        args,
        duration: `${duration}ms`,
      });
      throw error;
    }
  }

  getToolInfo(name) {
    return this.tools.get(name);
  }

  getToolsCount() {
    return this.tools.size;
  }
}
