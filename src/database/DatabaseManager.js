import { MongoClient } from 'mongodb';
import mongoose from 'mongoose';
import { Logger } from '../utils/Logger.js';

export class DatabaseManager {
  constructor() {
    this.logger = new Logger();
    this.client = null;
    this.db = null;
    this.isConnected = false;
    this.connectionString = process.env.MONGODB_URI || 'mongodb://localhost:27017/mcp_server';
    this.dbName = process.env.MONGODB_DB_NAME || 'mcp_server';
  }

  async connect() {
    try {
      this.logger.info('Conectando a MongoDB...');

      // Conectar usando el cliente nativo de MongoDB
      this.client = new MongoClient(this.connectionString);
      await this.client.connect();
      this.db = this.client.db(this.dbName);

      // También conectar Mongoose para esquemas más complejos
      await mongoose.connect(this.connectionString);

      this.isConnected = true;
      this.logger.info(`✅ Conectado a MongoDB: ${this.dbName}`);

      // Configurar índices básicos
      await this.setupIndexes();

    } catch (error) {
      this.logger.error('Error conectando a MongoDB:', error);
      throw error;
    }
  }

  async disconnect() {
    try {
      if (this.client) {
        await this.client.close();
      }
      if (mongoose.connection.readyState === 1) {
        await mongoose.disconnect();
      }
      this.isConnected = false;
      this.logger.info('✅ Desconectado de MongoDB');
    } catch (error) {
      this.logger.error('Error desconectando de MongoDB:', error);
    }
  }

  async setupIndexes() {
    try {
      // Crear índices básicos para optimización
      const collections = ['logs', 'queries', 'results'];
      
      for (const collectionName of collections) {
        const collection = this.db.collection(collectionName);
        
        // Índice por timestamp
        await collection.createIndex({ timestamp: 1 });
        
        // Índice por tipo (si existe)
        await collection.createIndex({ type: 1 }, { sparse: true });
      }
      
      this.logger.info('✅ Índices configurados');
    } catch (error) {
      this.logger.warn('Advertencia configurando índices:', error.message);
    }
  }

  /**
   * Ejecutar una consulta find
   */
  async find(collection, query = {}, options = {}) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const cursor = coll.find(query, options);
      
      if (options.limit) {
        cursor.limit(options.limit);
      }
      
      if (options.sort) {
        cursor.sort(options.sort);
      }
      
      const results = await cursor.toArray();
      
      this.logger.info(`Query find en ${collection}: ${results.length} documentos`);
      return results;
      
    } catch (error) {
      this.logger.error(`Error en find ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Ejecutar una consulta findOne
   */
  async findOne(collection, query = {}, options = {}) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const result = await coll.findOne(query, options);
      
      this.logger.info(`Query findOne en ${collection}: ${result ? 'encontrado' : 'no encontrado'}`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error en findOne ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Insertar un documento
   */
  async insertOne(collection, document) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const result = await coll.insertOne({
        ...document,
        createdAt: new Date(),
        updatedAt: new Date(),
      });
      
      this.logger.info(`Documento insertado en ${collection}: ${result.insertedId}`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error insertando en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Insertar múltiples documentos
   */
  async insertMany(collection, documents) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const timestamp = new Date();
      
      const documentsWithTimestamp = documents.map(doc => ({
        ...doc,
        createdAt: timestamp,
        updatedAt: timestamp,
      }));
      
      const result = await coll.insertMany(documentsWithTimestamp);
      
      this.logger.info(`${result.insertedCount} documentos insertados en ${collection}`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error insertando múltiples en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Actualizar un documento
   */
  async updateOne(collection, filter, update, options = {}) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      
      // Agregar timestamp de actualización
      const updateWithTimestamp = {
        ...update,
        $set: {
          ...update.$set,
          updatedAt: new Date(),
        },
      };
      
      const result = await coll.updateOne(filter, updateWithTimestamp, options);
      
      this.logger.info(`Documento actualizado en ${collection}: ${result.modifiedCount} modificado(s)`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error actualizando en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Actualizar múltiples documentos
   */
  async updateMany(collection, filter, update, options = {}) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      
      const updateWithTimestamp = {
        ...update,
        $set: {
          ...update.$set,
          updatedAt: new Date(),
        },
      };
      
      const result = await coll.updateMany(filter, updateWithTimestamp, options);
      
      this.logger.info(`Documentos actualizados en ${collection}: ${result.modifiedCount} modificado(s)`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error actualizando múltiples en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Eliminar un documento
   */
  async deleteOne(collection, filter) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const result = await coll.deleteOne(filter);
      
      this.logger.info(`Documento eliminado de ${collection}: ${result.deletedCount} eliminado(s)`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error eliminando en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Eliminar múltiples documentos
   */
  async deleteMany(collection, filter) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const result = await coll.deleteMany(filter);
      
      this.logger.info(`Documentos eliminados de ${collection}: ${result.deletedCount} eliminado(s)`);
      return result;
      
    } catch (error) {
      this.logger.error(`Error eliminando múltiples en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Contar documentos
   */
  async countDocuments(collection, filter = {}) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const count = await coll.countDocuments(filter);
      
      this.logger.info(`Conteo en ${collection}: ${count} documentos`);
      return count;
      
    } catch (error) {
      this.logger.error(`Error contando en ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Ejecutar agregación
   */
  async aggregate(collection, pipeline, options = {}) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const cursor = coll.aggregate(pipeline, options);
      const results = await cursor.toArray();
      
      this.logger.info(`Agregación en ${collection}: ${results.length} resultados`);
      return results;
      
    } catch (error) {
      this.logger.error(`Error en agregación ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Obtener esquema de una colección (muestra de documentos)
   */
  async getCollectionSchema(collection, sampleSize = 100) {
    this.ensureConnected();
    
    try {
      const coll = this.db.collection(collection);
      const sample = await coll.aggregate([
        { $sample: { size: sampleSize } }
      ]).toArray();
      
      if (sample.length === 0) {
        return { fields: [], sampleCount: 0 };
      }
      
      // Analizar estructura de campos
      const fields = new Set();
      const fieldTypes = {};
      
      sample.forEach(doc => {
        Object.keys(doc).forEach(key => {
          fields.add(key);
          const type = typeof doc[key];
          if (!fieldTypes[key]) {
            fieldTypes[key] = new Set();
          }
          fieldTypes[key].add(type);
        });
      });
      
      const schema = {
        collection,
        sampleCount: sample.length,
        fields: Array.from(fields).map(field => ({
          name: field,
          types: Array.from(fieldTypes[field])
        }))
      };
      
      this.logger.info(`Esquema obtenido para ${collection}: ${schema.fields.length} campos`);
      return schema;
      
    } catch (error) {
      this.logger.error(`Error obteniendo esquema de ${collection}:`, error);
      throw error;
    }
  }

  /**
   * Listar todas las colecciones
   */
  async listCollections() {
    this.ensureConnected();
    
    try {
      const collections = await this.db.listCollections().toArray();
      const collectionNames = collections.map(coll => coll.name);
      
      this.logger.info(`Colecciones disponibles: ${collectionNames.length}`);
      return collectionNames;
      
    } catch (error) {
      this.logger.error('Error listando colecciones:', error);
      throw error;
    }
  }

  /**
   * Ejecutar comando personalizado
   */
  async runCommand(command) {
    this.ensureConnected();
    
    try {
      const result = await this.db.command(command);
      this.logger.info('Comando ejecutado exitosamente');
      return result;
      
    } catch (error) {
      this.logger.error('Error ejecutando comando:', error);
      throw error;
    }
  }

  /**
   * Obtener estadísticas de la base de datos
   */
  async getDatabaseStats() {
    this.ensureConnected();
    
    try {
      const stats = await this.db.stats();
      this.logger.info('Estadísticas de base de datos obtenidas');
      return stats;
      
    } catch (error) {
      this.logger.error('Error obteniendo estadísticas:', error);
      throw error;
    }
  }

  /**
   * Verificar si está conectado
   */
  ensureConnected() {
    if (!this.isConnected) {
      throw new Error('No hay conexión activa a la base de datos');
    }
  }

  /**
   * Obtener información de conexión
   */
  getConnectionInfo() {
    return {
      connected: this.isConnected,
      database: this.dbName,
      connectionString: this.connectionString.replace(/\/\/.*:.*@/, '//***:***@'), // Ocultar credenciales
    };
  }
}
