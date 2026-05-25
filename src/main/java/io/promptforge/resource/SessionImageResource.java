package io.promptforge.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Path("/api/session-images")
public class SessionImageResource {

    @ConfigProperty(name = "app.upload.dir")
    String uploadDir;

    @GET
    @Path("/{sessionId}/{filename}")
    public Response getImage(@PathParam("sessionId") UUID sessionId, @PathParam("filename") String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid filename").build();
        }

        java.nio.file.Path baseDir = java.nio.file.Path.of(uploadDir).resolve("sessions")
                .resolve(sessionId.toString()).toAbsolutePath().normalize();
        java.nio.file.Path filePath = baseDir.resolve(filename).toAbsolutePath().normalize();

        if (!filePath.startsWith(baseDir)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (!Files.exists(filePath)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (IOException e) {
            contentType = "application/octet-stream";
        }

        return Response.ok(filePath.toFile())
                .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                .build();
    }
}
