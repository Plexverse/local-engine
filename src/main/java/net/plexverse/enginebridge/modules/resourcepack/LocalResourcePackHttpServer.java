package net.plexverse.enginebridge.modules.resourcepack;

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple HTTP server for serving resource packs locally.
 */
@Slf4j
public class LocalResourcePackHttpServer {
    
    private final HttpServer server;
    private final Path resourcePackDir;
    
    public LocalResourcePackHttpServer(final InetSocketAddress address, final Path resourcePackDir) throws IOException {
        this.resourcePackDir = resourcePackDir;
        this.server = HttpServer.create(address, 0);
        
        // Create context for serving resource packs
        server.createContext("/resourcepacks/", exchange -> {
            try {
                final String requestPath = exchange.getRequestURI().getPath();
                final String fileName = requestPath.substring("/resourcepacks/".length());
                
                // Security: prevent directory traversal
                if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                    exchange.sendResponseHeaders(403, 0);
                    exchange.close();
                    return;
                }
                
                final Path filePath = resourcePackDir.resolve(fileName);
                
                if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                    log.warn("Resource pack not found: {}", fileName);
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                    return;
                }
                
                // Set content type and headers
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                
                final byte[] fileData = Files.readAllBytes(filePath);
                exchange.sendResponseHeaders(200, fileData.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileData);
                }
                
                log.debug("Served resource pack: {}", fileName);
            } catch (Exception e) {
                log.error("Error serving resource pack", e);
                try {
                    exchange.sendResponseHeaders(500, 0);
                } catch (IOException ignored) {
                }
                exchange.close();
            }
        });
    }
    
    public void start() {
        server.start();
    }
    
    public void stop(final int delay) {
        server.stop(delay);
    }
}

