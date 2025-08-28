import OpenAI from 'openai';
import { GoogleGenerativeAI } from '@google/generative-ai';
import { HfInference } from '@huggingface/inference';
import { Logger } from '../utils/Logger.js';

export class LLMManager {
  constructor() {
    this.logger = new Logger();
    this.providers = new Map();
    this.initialized = false;
  }

  async initialize() {
    try {
      this.logger.info('Inicializando proveedores de LLM...');

      // Inicializar OpenAI (GPT)
      if (process.env.OPENAI_API_KEY) {
        const openai = new OpenAI({
          apiKey: process.env.OPENAI_API_KEY,
        });
        this.providers.set('openai', {
          client: openai,
          model: process.env.OPENAI_MODEL || 'gpt-4o-mini',
          maxTokens: parseInt(process.env.OPENAI_MAX_TOKENS) || 4096,
        });
        this.logger.info('✅ OpenAI (GPT) inicializado');
      }

      // Inicializar Google Gemini
      if (process.env.GOOGLE_API_KEY) {
        const genAI = new GoogleGenerativeAI(process.env.GOOGLE_API_KEY);
        const model = genAI.getGenerativeModel({ 
          model: process.env.GEMINI_MODEL || 'gemini-1.5-flash' 
        });
        this.providers.set('gemini', {
          client: model,
          model: process.env.GEMINI_MODEL || 'gemini-1.5-flash',
          maxTokens: parseInt(process.env.GEMINI_MAX_TOKENS) || 4096,
        });
        this.logger.info('✅ Google Gemini inicializado');
      }

      // Inicializar Hugging Face (Llama y otros)
      if (process.env.HUGGINGFACE_API_KEY) {
        const hf = new HfInference(process.env.HUGGINGFACE_API_KEY);
        this.providers.set('huggingface', {
          client: hf,
          model: process.env.HUGGINGFACE_MODEL || 'meta-llama/Llama-2-70b-chat-hf',
          maxTokens: parseInt(process.env.HUGGINGFACE_MAX_TOKENS) || 4096,
        });
        this.logger.info('✅ Hugging Face (Llama) inicializado');
      }

      if (this.providers.size === 0) {
        throw new Error('No se configuraron proveedores de LLM. Revisa las variables de entorno.');
      }

      this.initialized = true;
      this.logger.info(`🎯 ${this.providers.size} proveedores de LLM inicializados`);

    } catch (error) {
      this.logger.error('Error inicializando LLMs:', error);
      throw error;
    }
  }

  /**
   * Genera texto usando el proveedor especificado
   */
  async generateText(prompt, provider = 'openai', options = {}) {
    if (!this.initialized) {
      throw new Error('LLMManager no ha sido inicializado');
    }

    if (!this.providers.has(provider)) {
      throw new Error(`Proveedor ${provider} no disponible`);
    }

    try {
      const providerConfig = this.providers.get(provider);
      const maxTokens = options.maxTokens || providerConfig.maxTokens;

      switch (provider) {
        case 'openai':
          return await this.generateWithOpenAI(prompt, providerConfig, maxTokens, options);
        
        case 'gemini':
          return await this.generateWithGemini(prompt, providerConfig, maxTokens, options);
        
        case 'huggingface':
          return await this.generateWithHuggingFace(prompt, providerConfig, maxTokens, options);
        
        default:
          throw new Error(`Proveedor ${provider} no soportado`);
      }

    } catch (error) {
      this.logger.error(`Error generando texto con ${provider}:`, error);
      throw error;
    }
  }

  /**
   * Generar texto con OpenAI
   */
  async generateWithOpenAI(prompt, config, maxTokens, options) {
    const response = await config.client.chat.completions.create({
      model: config.model,
      messages: [
        {
          role: 'system',
          content: options.systemPrompt || 'Eres un asistente útil y preciso.',
        },
        {
          role: 'user',
          content: prompt,
        }
      ],
      max_tokens: maxTokens,
      temperature: options.temperature || 0.7,
      top_p: options.topP || 1,
    });

    return response.choices[0].message.content;
  }

  /**
   * Generar texto con Gemini
   */
  async generateWithGemini(prompt, config, maxTokens, options) {
    const systemPrompt = options.systemPrompt || 'Eres un asistente útil y preciso.';
    const fullPrompt = `${systemPrompt}\n\nUsuario: ${prompt}\nAsistente:`;

    const result = await config.client.generateContent({
      contents: [{ role: 'user', parts: [{ text: fullPrompt }] }],
      generationConfig: {
        maxOutputTokens: maxTokens,
        temperature: options.temperature || 0.7,
        topP: options.topP || 1,
      },
    });

    return result.response.text();
  }

  /**
   * Generar texto con Hugging Face
   */
  async generateWithHuggingFace(prompt, config, maxTokens, options) {
    const systemPrompt = options.systemPrompt || 'Eres un asistente útil y preciso.';
    const fullPrompt = `<s>[INST] <<SYS>>\n${systemPrompt}\n<</SYS>>\n\n${prompt} [/INST]`;

    const response = await config.client.textGeneration({
      model: config.model,
      inputs: fullPrompt,
      parameters: {
        max_new_tokens: maxTokens,
        temperature: options.temperature || 0.7,
        top_p: options.topP || 0.9,
        do_sample: true,
      },
    });

    return response.generated_text.replace(fullPrompt, '').trim();
  }

  /**
   * Ejecutar una consulta SQL usando LLM
   */
  async generateSQLQuery(naturalLanguageQuery, schema, provider = 'openai') {
    const systemPrompt = `
Eres un experto en bases de datos MongoDB. Tu tarea es convertir consultas en lenguaje natural a operaciones MongoDB.

ESQUEMA DE LA BASE DE DATOS:
${JSON.stringify(schema, null, 2)}

INSTRUCCIONES:
- Devuelve SOLO el código JavaScript/MongoDB, sin explicaciones
- Usa la sintaxis correcta de MongoDB
- Para consultas complejas, usa aggregation pipeline
- Asegúrate de que la consulta sea eficiente

Ejemplos de formato de respuesta:
- Para find: db.collection.find({campo: valor})
- Para aggregation: db.collection.aggregate([{$match: {}}, {$group: {}}])
- Para count: db.collection.countDocuments({})
`;

    const prompt = `Convierte esta consulta a MongoDB: "${naturalLanguageQuery}"`;

    const response = await this.generateText(prompt, provider, {
      systemPrompt,
      temperature: 0.1, // Más determinista para SQL
    });

    return response.trim();
  }

  /**
   * Analizar datos usando LLM
   */
  async analyzeData(data, question, provider = 'openai') {
    const systemPrompt = `
Eres un analista de datos experto. Analiza los datos proporcionados y responde la pregunta del usuario.

INSTRUCCIONES:
- Proporciona respuestas precisas y basadas en los datos
- Incluye números específicos cuando sea relevante
- Si encuentras patrones interesantes, méncionalos
- Sé conciso pero informativo
`;

    const prompt = `
DATOS:
${JSON.stringify(data, null, 2)}

PREGUNTA: ${question}

ANÁLISIS:
`;

    return await this.generateText(prompt, provider, {
      systemPrompt,
      temperature: 0.3,
    });
  }

  /**
   * Obtener proveedores disponibles
   */
  getAvailableProviders() {
    return Array.from(this.providers.keys());
  }

  /**
   * Verificar si un proveedor está disponible
   */
  isProviderAvailable(provider) {
    return this.providers.has(provider);
  }

  /**
   * Obtener información de un proveedor
   */
  getProviderInfo(provider) {
    if (!this.providers.has(provider)) {
      return null;
    }

    const config = this.providers.get(provider);
    return {
      model: config.model,
      maxTokens: config.maxTokens,
    };
  }
}
