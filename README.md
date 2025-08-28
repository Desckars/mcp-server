# MCP Server

![Node.js](https://img.shields.io/badge/Node.js-18%2B-green)
![License](https://img.shields.io/badge/License-MIT-blue)

Servidor MCP (Model Context Protocol) para integración con asistentes de IA como Claude.

## ¿Qué es MCP?

El Model Context Protocol (MCP) es un protocolo estándar que permite a los asistentes de IA conectarse de forma segura a fuentes de datos locales y remotas. Este servidor implementa las capacidades básicas de MCP para proporcionar herramientas personalizadas.

## Características

- ✅ Implementación completa del protocolo MCP
- ✅ Herramientas de ejemplo (echo, get_time)
- ✅ Manejo de errores robusto
- ✅ Fácil extensión para nuevas herramientas
- ✅ Compatible con Claude Desktop

## Instalación

### Prerrequisitos

- Node.js 18 o superior
- npm o yarn

### Pasos

1. Clona el repositorio:
```bash
git clone https://github.com/Desckars/mcp-server.git
cd mcp-server
```

2. Instala las dependencias:
```bash
npm install
```

3. Ejecuta el servidor:
```bash
npm start
```

## Uso con Claude Desktop

Para usar este servidor con Claude Desktop, agrega la siguiente configuración a tu archivo `claude_desktop_config.json`:

### Windows
Ubicación: `%APPDATA%\Claude\claude_desktop_config.json`

### macOS
Ubicación: `~/Library/Application Support/Claude/claude_desktop_config.json`

### Linux
Ubicación: `~/.config/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "mcp-server": {
      "command": "node",
      "args": ["/ruta/completa/a/tu/mcp-server/src/index.js"],
      "env": {}
    }
  }
}
```

**Importante:** Reemplaza `/ruta/completa/a/tu/mcp-server` con la ruta real donde clonaste este repositorio.

## Herramientas Disponibles

### 1. Echo
- **Descripción:** Repite el mensaje proporcionado
- **Parámetros:** `message` (string)
- **Ejemplo:** Echo "Hola mundo"

### 2. Get Time
- **Descripción:** Obtiene la hora actual en formato ISO
- **Parámetros:** Ninguno
- **Ejemplo:** Devuelve la hora actual del sistema

## Desarrollo

### Modo desarrollo
```bash
npm run dev
```

### Estructura del proyecto
```
mcp-server/
├── src/
│   └── index.js          # Servidor principal
├── package.json          # Configuración del proyecto
├── README.md            # Documentación
└── .gitignore          # Archivos ignorados por Git
```

### Agregar nuevas herramientas

1. En `src/index.js`, agrega la definición de la herramienta en `ListToolsRequestSchema`
2. Implementa el manejo en `CallToolRequestSchema`
3. Reinicia el servidor

## Configuración Avanzada

### Variables de entorno

Puedes configurar el servidor usando variables de entorno:

- `MCP_SERVER_NAME`: Nombre del servidor (default: 'mcp-server')
- `MCP_SERVER_VERSION`: Versión del servidor (default: '1.0.0')

### Logging

El servidor registra errores y eventos importantes. Para debugging, puedes usar:

```bash
DEBUG=mcp* npm start
```

## Solución de Problemas

### El servidor no aparece en Claude Desktop

1. Verifica que la ruta en `claude_desktop_config.json` sea correcta
2. Asegúrate de que Node.js esté instalado y accesible
3. Reinicia Claude Desktop completamente
4. Verifica los logs en la consola del desarrollador de Claude

### Error de permisos

```bash
chmod +x src/index.js
```

### Problemas de dependencias

```bash
rm -rf node_modules package-lock.json
npm install
```

## Contribuir

1. Fork el repositorio
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

## Recursos

- [Documentación oficial de MCP](https://modelcontextprotocol.io/)
- [SDK de MCP para JavaScript](https://github.com/modelcontextprotocol/typescript-sdk)
- [Claude Desktop](https://claude.ai/download)

## Soporte

Si encuentras algún problema o tienes preguntas, por favor abre un issue en este repositorio.
