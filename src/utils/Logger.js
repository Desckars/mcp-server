import winston from 'winston';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export class Logger {
  constructor(module = 'MCP-Server') {
    this.module = module;
    this.logger = this.createLogger();
  }

  createLogger() {
    // Crear directorio de logs si no existe
    const logDir = path.join(process.cwd(), 'logs');
    if (!fs.existsSync(logDir)) {
      fs.mkdirSync(logDir, { recursive: true });
    }

    // Configurar formato personalizado
    const customFormat = winston.format.combine(
      winston.format.timestamp({
        format: 'YYYY-MM-DD HH:mm:ss'
      }),
      winston.format.errors({ stack: true }),
      winston.format.printf(({ timestamp, level, message, stack, module: logModule, ...meta }) => {
        const moduleInfo = logModule || this.module;
        let logMessage = `${timestamp} [${level.toUpperCase()}] [${moduleInfo}]: ${message}`;
        
        if (stack) {
          logMessage += `\n${stack}`;
        }
        
        if (Object.keys(meta).length > 0) {
          logMessage += `\n${JSON.stringify(meta, null, 2)}`;
        }
        
        return logMessage;
      })
    );

    // Configurar transports
    const transports = [
      // Console transport
      new winston.transports.Console({
        level: process.env.LOG_LEVEL || 'info',
        format: winston.format.combine(
          winston.format.colorize(),
          customFormat
        )
      }),
    ];

    // File transport para producción
    if (process.env.NODE_ENV === 'production' || process.env.LOG_FILE) {
      const logFile = process.env.LOG_FILE || path.join(logDir, 'mcp-server.log');
      
      transports.push(
        new winston.transports.File({
          filename: logFile,
          level: process.env.LOG_LEVEL || 'info',
          format: customFormat,
          maxsize: 5242880, // 5MB
          maxFiles: 10,
        })
      );

      // Archivo separado para errores
      transports.push(
        new winston.transports.File({
          filename: path.join(logDir, 'error.log'),
          level: 'error',
          format: customFormat,
          maxsize: 5242880, // 5MB
          maxFiles: 5,
        })
      );
    }

    return winston.createLogger({
      level: process.env.LOG_LEVEL || 'info',
      format: customFormat,
      transports,
      exitOnError: false,
    });
  }

  info(message, meta = {}) {
    this.logger.info(message, { module: this.module, ...meta });
  }

  error(message, error = null, meta = {}) {
    if (error instanceof Error) {
      this.logger.error(message, { 
        module: this.module, 
        stack: error.stack,
        ...meta 
      });
    } else {
      this.logger.error(message, { module: this.module, error, ...meta });
    }
  }

  warn(message, meta = {}) {
    this.logger.warn(message, { module: this.module, ...meta });
  }

  debug(message, meta = {}) {
    this.logger.debug(message, { module: this.module, ...meta });
  }

  verbose(message, meta = {}) {
    this.logger.verbose(message, { module: this.module, ...meta });
  }

  // Métodos específicos para el contexto MCP
  logToolExecution(toolName, args, result, duration) {
    this.info(`Tool executed: ${toolName}`, {
      tool: toolName,
      arguments: args,
      result: typeof result === 'object' ? 'object' : result,
      duration: `${duration}ms`,
      type: 'tool_execution'
    });
  }

  logLLMRequest(provider, prompt, response, duration) {
    this.info(`LLM request: ${provider}`, {
      provider,
      promptLength: prompt.length,
      responseLength: response.length,
      duration: `${duration}ms`,
      type: 'llm_request'
    });
  }

  logDatabaseOperation(operation, collection, query, resultCount) {
    this.info(`Database operation: ${operation}`, {
      operation,
      collection,
      query: typeof query === 'object' ? JSON.stringify(query) : query,
      resultCount,
      type: 'database_operation'
    });
  }

  logError(error, context = {}) {
    this.error('Application error occurred', error, {
      context,
      type: 'application_error'
    });
  }

  // Crear logger hijo con contexto específico
  child(childContext) {
    return new Logger(`${this.module}:${childContext}`);
  }

  // Configurar nivel de log dinámicamente
  setLevel(level) {
    this.logger.level = level;
    this.logger.transports.forEach(transport => {
      transport.level = level;
    });
    this.info(`Log level changed to: ${level}`);
  }

  // Obtener nivel actual
  getLevel() {
    return this.logger.level;
  }

  // Método para logging de requests HTTP (si se implementa servidor HTTP)
  logHTTPRequest(req, res, duration) {
    const { method, url, headers, ip } = req;
    const { statusCode } = res;
    
    this.info(`HTTP ${method} ${url}`, {
      method,
      url,
      statusCode,
      userAgent: headers['user-agent'],
      ip,
      duration: `${duration}ms`,
      type: 'http_request'
    });
  }

  // Logging para métricas de rendimiento
  logPerformanceMetric(metric, value, unit = 'ms', tags = {}) {
    this.info(`Performance metric: ${metric}`, {
      metric,
      value,
      unit,
      tags,
      type: 'performance_metric'
    });
  }

  // Logging para eventos de sistema
  logSystemEvent(event, data = {}) {
    this.info(`System event: ${event}`, {
      event,
      data,
      type: 'system_event'
    });
  }
}

// Logger singleton para uso global
export const logger = new Logger('MCP-Server');

// Función helper para crear loggers con contexto
export function createLogger(context) {
  return new Logger(context);
}
