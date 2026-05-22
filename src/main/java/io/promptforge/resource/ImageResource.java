package io.promptforge.resource;

import io.promptforge.entity.CharacterEntity;
import io.promptforge.repository.CharacterRepository;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Path("/api")
public class ImageResource {

    @Inject
    CharacterRepository characterRepository;

    @ConfigProperty(name = "app.upload.dir")
    String uploadDir;

    @POST
    @Path("/characters/{id}/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public Response uploadAvatar(@PathParam("id") UUID id, @FormParam("file") FileUpload fileUpload) {
        CharacterEntity character = characterRepository.findByIdOptional(id).orElse(null);
        if (character == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("角色不存在").build();
        }

        if (fileUpload == null || fileUpload.filePath() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("请选择文件").build();
        }

        String contentType = fileUpload.contentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Response.status(Response.Status.BAD_REQUEST).entity("只支持图片文件").build();
        }

        try {
            long size = Files.size(fileUpload.filePath());
            if (size == 0) {
                return Response.status(Response.Status.BAD_REQUEST).entity("文件不能为空").build();
            }
            if (size > 5L * 1024 * 1024) {
                return Response.status(Response.Status.BAD_REQUEST).entity("文件大小不能超过 5MB").build();
            }
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("无法读取文件").build();
        }

        // Delete old avatar file before saving new one
        if (character.avatarPath != null) {
            try {
                Files.deleteIfExists(java.nio.file.Path.of(character.avatarPath));
            } catch (IOException e) {
                Log.warn("删除旧头像失败: " + e.getMessage());
            }
        }

        String originalName = fileUpload.fileName();
        if (originalName == null || originalName.isBlank()) {
            originalName = "avatar";
        }
        String safeName = sanitizeFilename(originalName);
        String fileName = System.currentTimeMillis() + "_" + safeName;

        java.nio.file.Path targetDir = java.nio.file.Path.of(uploadDir).resolve("characters").resolve(id.toString());
        java.nio.file.Path targetPath = targetDir.resolve(fileName);

        try {
            Files.createDirectories(targetDir);
            Files.copy(fileUpload.filePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("保存文件失败: " + e.getMessage()).build();
        }

        character.avatarPath = targetPath.toString();
        characterRepository.persist(character);

        String avatarUrl = "/api/images/" + id + "/" + fileName;
        return Response.ok(avatarUrl).build();
    }

    @GET
    @Path("/images/{characterId}/{filename}")
    public Response getImage(@PathParam("characterId") UUID characterId, @PathParam("filename") String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid filename").build();
        }

        java.nio.file.Path baseDir = java.nio.file.Path.of(uploadDir).resolve("characters")
                .resolve(characterId.toString()).toAbsolutePath().normalize();
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

    private String sanitizeFilename(String filename) {
        String base = java.nio.file.Path.of(filename).getFileName().toString();
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
