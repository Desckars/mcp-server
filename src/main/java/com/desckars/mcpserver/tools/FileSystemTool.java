package com.desckars.mcpserver.tools;

import com.desckars.mcpserver.handlers.ToolManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Herramienta universal de sistema de archivos para operaciones seguras
 * 
 * Compatible con múltiples LLMs y implementa operaciones básicas de archivos
 * con restricciones de seguridad para prevenir acceso no autorizado.
 * Soporta: lectura, escritura, listado, información de archivos y directorios.
 */
public class FileSystemTool implements ToolManager.MCPTool {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemTool.class);
    
    // Directorio base permitido (directorio de trabajo actual por defecto)
    private final Path basePath;
    private final int maxFileSize = 5 * 1024 * 1024; // 5MB máximo por archivo
    private final int maxListItems = 1000; // Máximo 1000 items en listado
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FileSystemTool() {
        // Configurar directorio base seguro
        this.basePath = Paths.get(System.getProperty("user.dir")).resolve("workspace");
        
        // Crear directorio workspace si no existe
        try {
            Files.createDirectories(basePath);
            logger.info("📁 FileSystemTool inicializado con workspace: {}", basePath);
        } catch (IOException e) {
            logger.warn("⚠️ No se pudo crear directorio workspace: {}", e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "filesystem";
    }

    @Override
    public String getDescription() {
        return "Operaciones seguras de sistema de archivos: leer, escribir, listar archivos y obtener información de forma controlada.";
    }
    
    @Override
    public String getCategory() {
        return "filesystem";
    }

    @Override
    public ObjectNode getInputSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = mapper.createObjectNode();
        
        // Acción
        ObjectNode action = mapper.createObjectNode();
        action.put("type", "string");
        action.put("description", "Operación a realizar en el sistema de archivos");
        
        ArrayNode enumValues = mapper.createArrayNode();
        enumValues.add("read");
        enumValues.add("write");
        enumValues.add("list");
        enumValues.add("info");
        enumValues.add("exists");
        enumValues.add("delete");
        enumValues.add("mkdir");
        action.set("enum", enumValues);
        
        properties.set("action", action);
        
        // Ruta del archivo
        ObjectNode path = mapper.createObjectNode();
        path.put("type", "string");
        path.put("description", "Ruta relativa del archivo o directorio (relativa al workspace)");
        path.put("pattern", "^[a-zA-Z0-9._/\\\\-]+$");
        properties.set("path", path);
        
        // Contenido (para escritura)
        ObjectNode content = mapper.createObjectNode();
        content.put("type", "string");
        content.put("description", "Contenido a escribir (solo para acción 'write')");
        content.put("maxLength", maxFileSize);
        properties.set("content", content);
        
        // Codificación (opcional)
        ObjectNode encoding = mapper.createObjectNode();
        encoding.put("type", "string");
        encoding.put("description", "Codificación del archivo (por defecto: UTF-8)");
        encoding.put("default", "UTF-8");
        ArrayNode encodingEnum = mapper.createArrayNode();
        encodingEnum.add("UTF-8");
        encodingEnum.add("ASCII");
        encodingEnum.add("ISO-8859-1");
        encoding.set("enum", encodingEnum);
        properties.set("encoding", encoding);
        
        // Recursivo para listado
        ObjectNode recursive = mapper.createObjectNode();
        recursive.put("type", "boolean");
        recursive.put("description", "Si el listado debe ser recursivo (solo para 'list')");
        recursive.put("default", false);
        properties.set("recursive", recursive);
        
        schema.set("properties", properties);
        
        ArrayNode required = mapper.createArrayNode();
        required.add("action");
        required.add("path");
        schema.set("required", required);
        
        return schema;
    }

    @Override
    public String execute(JsonNode arguments, ObjectMapper mapper) throws Exception {
        logger.debug("📁 Ejecutando operación de sistema de archivos: {}", arguments);
        
        // Validar argumentos
        validateArguments(arguments);
        
        String action = arguments.get("action").asText();
        String relativePath = arguments.get("path").asText();
        
        // Resolver y validar ruta
        Path targetPath = resolveSafePath(relativePath);
        
        // Ejecutar acción correspondiente
        String result;
        switch (action) {
            case "read":
                result = readFile(targetPath, arguments, mapper);
                break;
            case "write":
                String content = arguments.has("content") ? arguments.get("content").asText() : "";
                result = writeFile(targetPath, content, arguments, mapper);
                break;
            case "list":
                boolean recursive = arguments.has("recursive") && arguments.get("recursive").asBoolean();
                result = listDirectory(targetPath, recursive, mapper);
                break;
            case "info":
                result = getFileInfo(targetPath, mapper);
                break;
            case "exists":
                result = checkExists(targetPath, mapper);
                break;
            case "delete":
                result = deleteFile(targetPath, mapper);
                break;
            case "mkdir":
                result = createDirectory(targetPath, mapper);
                break;
            default:
                throw new IllegalArgumentException("Acción no soportada: " + action);
        }
        
        return result;
    }

    /**
     * Valida los argumentos de entrada
     */
    private void validateArguments(JsonNode arguments) throws IllegalArgumentException {
        if (arguments == null || !arguments.isObject()) {
            throw new IllegalArgumentException("Los argumentos deben ser un objeto JSON");
        }
        
        if (!arguments.has("action")) {
            throw new IllegalArgumentException("Falta el argumento 'action'");
        }
        
        if (!arguments.has("path")) {
            throw new IllegalArgumentException("Falta el argumento 'path'");
        }
        
        String action = arguments.get("action").asText();
        if ("write".equals(action) && !arguments.has("content")) {
            throw new IllegalArgumentException("La acción 'write' requiere el argumento 'content'");
        }
        
        // Validar caracteres de la ruta
        String path = arguments.get("path").asText();
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            throw new SecurityException("Ruta no permitida: " + path);
        }
    }

    /**
     * Resuelve una ruta de forma segura dentro del directorio base
     */
    private Path resolveSafePath(String relativePath) throws SecurityException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("La ruta no puede estar vacía");
        }
        
        // Limpiar la ruta
        relativePath = relativePath.replace("\\", "/");
        if (relativePath.startsWith("./")) {
            relativePath = relativePath.substring(2);
        }
        
        // Normalizar la ruta
        Path requested = Paths.get(relativePath).normalize();
        
        // Verificar que no contenga elementos peligrosos
        if (requested.toString().contains("..") || requested.isAbsolute()) {
            throw new SecurityException("Ruta no permitida: " + relativePath);
        }
        
        // Resolver contra el directorio base
        Path resolved = basePath.resolve(requested).normalize();
        
        // Verificar que esté dentro del directorio base
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("Acceso fuera del directorio permitido: " + relativePath);
        }
        
        return resolved;
    }

    /**
     * Lee el contenido de un archivo
     */
    private String readFile(Path filePath, JsonNode arguments, ObjectMapper mapper) throws Exception {
        if (!Files.exists(filePath)) {
            return createErrorResponse(mapper, "Archivo no encontrado: " + getRelativePath(filePath));
        }
        
        if (!Files.isRegularFile(filePath)) {
            return createErrorResponse(mapper, "La ruta no es un archivo: " + getRelativePath(filePath));
        }
        
        // Verificar tamaño del archivo
        long size = Files.size(filePath);
        if (size > maxFileSize) {
            return createErrorResponse(mapper, 
                String.format("Archivo demasiado grande: %d bytes (máximo: %d bytes)", size, maxFileSize));
        }
        
        try {
            String encoding = arguments.has("encoding") ? arguments.get("encoding").asText() : "UTF-8";
            String content = Files.readString(filePath, java.nio.charset.Charset.forName(encoding));
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentArray = mapper.createArrayNode();
            
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", String.format("📄 Contenido de %s (%d bytes):\n\n%s", 
                getRelativePath(filePath), size, content));
            contentArray.add(textContent);
            
            response.set("content", contentArray);
            response.put("isError", false);
            
            // Metadatos
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("path", getRelativePath(filePath));
            metadata.put("size", size);
            metadata.put("encoding", encoding);
            response.set("_metadata", metadata);
            
            return mapper.writeValueAsString(response);
            
        } catch (IOException e) {
            return createErrorResponse(mapper, "Error leyendo archivo: " + e.getMessage());
        }
    }

    /**
     * Escribe contenido a un archivo
     */
    private String writeFile(Path filePath, String content, JsonNode arguments, ObjectMapper mapper) throws Exception {
        try {
            // Crear directorios padre si no existen
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            
            String encoding = arguments.has("encoding") ? arguments.get("encoding").asText() : "UTF-8";
            
            // Escribir archivo
            Files.writeString(filePath, content, 
                java.nio.charset.Charset.forName(encoding),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentArray = mapper.createArrayNode();
            
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", String.format("✅ Archivo escrito exitosamente: %s (%d caracteres)", 
                getRelativePath(filePath), content.length()));
            contentArray.add(textContent);
            
            response.set("content", contentArray);
            response.put("isError", false);
            
            // Metadatos
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("path", getRelativePath(filePath));
            metadata.put("size", content.getBytes(encoding).length);
            metadata.put("encoding", encoding);
            response.set("_metadata", metadata);
            
            return mapper.writeValueAsString(response);
            
        } catch (IOException e) {
            return createErrorResponse(mapper, "Error escribiendo archivo: " + e.getMessage());
        }
    }

    /**
     * Lista el contenido de un directorio
     */
    private String listDirectory(Path dirPath, boolean recursive, ObjectMapper mapper) throws Exception {
        if (!Files.exists(dirPath)) {
            return createErrorResponse(mapper, "Directorio no encontrado: " + getRelativePath(dirPath));
        }
        
        if (!Files.isDirectory(dirPath)) {
            return createErrorResponse(mapper, "La ruta no es un directorio: " + getRelativePath(dirPath));
        }
        
        try {
            StringBuilder listing = new StringBuilder();
            listing.append(String.format("📂 Contenido de %s%s:\n\n", 
                getRelativePath(dirPath), recursive ? " (recursivo)" : ""));
            
            if (recursive) {
                listDirectoryRecursive(dirPath, listing, 0);
            } else {
                try (Stream<Path> paths = Files.list(dirPath).limit(maxListItems)) {
                    List<Path> sortedPaths = paths.sorted().toList();
                    
                    for (Path path : sortedPaths) {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        String type = attrs.isDirectory() ? "📁 DIR " : "📄 FILE";
                        String size = attrs.isDirectory() ? "" : String.format(" (%d bytes)", attrs.size());
                        String modified = attrs.lastModifiedTime().toString();
                        
                        listing.append(String.format("%s %s%s - %s\n", 
                            type, path.getFileName(), size, modified));
                    }
                    
                    if (sortedPaths.isEmpty()) {
                        listing.append("(Directorio vacío)");
                    } else if (sortedPaths.size() >= maxListItems) {
                        listing.append(String.format("\n... (mostrados primeros %d elementos)\n", maxListItems));
                    }
                }
            }
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentArray = mapper.createArrayNode();
            
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", listing.toString());
            contentArray.add(textContent);
            
            response.set("content", contentArray);
            response.put("isError", false);
            
            // Metadatos
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("path", getRelativePath(dirPath));
            metadata.put("recursive", recursive);
            response.set("_metadata", metadata);
            
            return mapper.writeValueAsString(response);
            
        } catch (IOException e) {
            return createErrorResponse(mapper, "Error listando directorio: " + e.getMessage());
        }
    }

    /**
     * Lista directorio de forma recursiva
     */
    private void listDirectoryRecursive(Path dir, StringBuilder listing, int depth) throws IOException {
        String indent = "  ".repeat(depth);
        
        try (Stream<Path> paths = Files.list(dir).limit(maxListItems)) {
            List<Path> sortedPaths = paths.sorted().toList();
            
            for (Path path : sortedPaths) {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                String type = attrs.isDirectory() ? "📁" : "📄";
                String size = attrs.isDirectory() ? "" : String.format(" (%d bytes)", attrs.size());
                
                listing.append(String.format("%s%s %s%s\n", 
                    indent, type, path.getFileName(), size));
                
                if (attrs.isDirectory() && depth < 3) { // Limitar profundidad
                    listDirectoryRecursive(path, listing, depth + 1);
                }
            }
        }
    }

    /**
     * Obtiene información sobre un archivo o directorio
     */
    private String getFileInfo(Path path, ObjectMapper mapper) throws Exception {
        if (!Files.exists(path)) {
            return createErrorResponse(mapper, "Archivo o directorio no encontrado: " + getRelativePath(path));
        }
        
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            StringBuilder info = new StringBuilder();
            info.append(String.format("ℹ️ Información de %s:\n\n", getRelativePath(path)));
            info.append(String.format("Tipo: %s\n", attrs.isDirectory() ? "Directorio" : "Archivo"));
            info.append(String.format("Tamaño: %d bytes\n", attrs.size()));
            info.append(String.format("Creado: %s\n", attrs.creationTime()));
            info.append(String.format("Modificado: %s\n", attrs.lastModifiedTime()));
            info.append(String.format("Accedido: %s\n", attrs.lastAccessTime()));
            info.append(String.format("Permisos - Lectura: %s, Escritura: %s\n", 
                Files.isReadable(path) ? "Sí" : "No",
                Files.isWritable(path) ? "Sí" : "No"));
            
            if (attrs.isRegularFile()) {
                try {
                    String mimeType = Files.probeContentType(path);
                    if (mimeType != null) {
                        info.append(String.format("Tipo MIME: %s\n", mimeType));
                    }
                } catch (IOException e) {
                    // Ignorar si no se puede determinar el tipo MIME
                }
            }
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentArray = mapper.createArrayNode();
            
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", info.toString());
            contentArray.add(textContent);
            
            response.set("content", contentArray);
            response.put("isError", false);
            
            // Metadatos
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("path", getRelativePath(path));
            metadata.put("type", attrs.isDirectory() ? "directory" : "file");
            metadata.put("size", attrs.size());
            metadata.put("readable", Files.isReadable(path));
            metadata.put("writable", Files.isWritable(path));
            response.set("_metadata", metadata);
            
            return mapper.writeValueAsString(response);
            
        } catch (IOException e) {
            return createErrorResponse(mapper, "Error obteniendo información: " + e.getMessage());
        }
    }

    /**
     * Verifica si un archivo o directorio existe
     */
    private String checkExists(Path path, ObjectMapper mapper) throws Exception {
        boolean exists = Files.exists(path);
        
        ObjectNode response = mapper.createObjectNode();
        ArrayNode contentArray = mapper.createArrayNode();
        
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", String.format("%s %s %s", 
            exists ? "✅" : "❌",
            getRelativePath(path),
            exists ? "existe" : "no existe"));
        contentArray.add(textContent);
        
        response.set("content", contentArray);
        response.put("isError", false);
        
        // Metadatos
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("path", getRelativePath(path));
        metadata.put("exists", exists);
        response.set("_metadata", metadata);
        
        return mapper.writeValueAsString(response);
    }

    /**
     * Elimina un archivo o directorio
     */
    private String deleteFile(Path path, ObjectMapper mapper) throws Exception {
        if (!Files.exists(path)) {
            return createErrorResponse(mapper, "Archivo o directorio no encontrado: " + getRelativePath(path));
        }
        
        try {
            boolean isDirectory = Files.isDirectory(path);
            String type = isDirectory ? "directorio" : "archivo";
            
            if (isDirectory) {
                // Eliminar directorio y su contenido
                Files.walk(path)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } else {
                Files.delete(path);
            }
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentArray = mapper.createArrayNode();
            
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", String.format("🗑️ %s eliminado exitosamente: %s", 
                type.substring(0, 1).toUpperCase() + type.substring(1),
                getRelativePath(path)));
            contentArray.add(textContent);
            
            response.set("content", contentArray);
            response.put("isError", false);
            
            // Metadatos
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("path", getRelativePath(path));
            metadata.put("action", "deleted");
            metadata.put("type", isDirectory ? "directory" : "file");
            response.set("_metadata", metadata);
            
            return mapper.writeValueAsString(response);
            
        } catch (IOException e) {
            return createErrorResponse(mapper, "Error eliminando: " + e.getMessage());
        }
    }

    /**
     * Crea un directorio
     */
    private String createDirectory(Path path, ObjectMapper mapper) throws Exception {
        if (Files.exists(path)) {
            return createErrorResponse(mapper, "El directorio ya existe: " + getRelativePath(path));
        }
        
        try {
            Files.createDirectories(path);
            
            ObjectNode response = mapper.createObjectNode();
            ArrayNode contentArray = mapper.createArrayNode();
            
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", String.format("📁 Directorio creado exitosamente: %s", 
                getRelativePath(path)));
            contentArray.add(textContent);
            
            response.set("content", contentArray);
            response.put("isError", false);
            
            // Metadatos
            ObjectNode metadata = mapper.createObjectNode();
            metadata.put("path", getRelativePath(path));
            metadata.put("action", "created");
            metadata.put("type", "directory");
            response.set("_metadata", metadata);
            
            return mapper.writeValueAsString(response);
            
        } catch (IOException e) {
            return createErrorResponse(mapper, "Error creando directorio: " + e.getMessage());
        }
    }

    /**
     * Obtiene la ruta relativa al directorio base
     */
    private String getRelativePath(Path path) {
        return basePath.relativize(path).toString().replace("\\", "/");
    }

    /**
     * Crea una respuesta de error
     */
    private String createErrorResponse(ObjectMapper mapper, String errorMessage) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        ArrayNode contentArray = mapper.createArrayNode();
        
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "❌ Error: " + errorMessage);
        contentArray.add(textContent);
        
        response.set("content", contentArray);
        response.put("isError", true);
        
        return mapper.writeValueAsString(response);
    }
}