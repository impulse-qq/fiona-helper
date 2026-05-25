package io.promptforge.resource;

import io.promptforge.dto.*;
import io.promptforge.entity.*;
import io.promptforge.repository.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource {

    @Inject
    AssembleSessionRepository sessionRepository;

    @Inject
    PipelineRepository pipelineRepository;

    @Inject
    SlotRepository slotRepository;

    @Inject
    SlotPromptRepository slotPromptRepository;

    @Inject
    CharacterRepository characterRepository;

    @Inject
    SessionImageRepository sessionImageRepository;

    @ConfigProperty(name = "app.upload.dir")
    String uploadDir;

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        AssembleSessionEntity session = sessionRepository.findByIdOptional(id).orElse(null);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PipelineEntity pipeline = pipelineRepository.findByIdOptional(session.pipelineId).orElse(null);
        if (pipeline == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String characterName = null;
        if (session.characterId != null) {
            characterName = characterRepository.findByIdOptional(session.characterId)
                    .map(c -> c.name).orElse(null);
        }

        // Load slots in order
        List<SlotEntity> slots = slotRepository.findByPipelineId(session.pipelineId);
        slots.sort(Comparator.comparingInt(s -> s.orderIndex));

        // Load session-scoped prompts
        List<SlotPromptEntity> prompts = slotPromptRepository.findBySessionId(id);
        Map<UUID, String> contentBySlotId = prompts.stream()
                .collect(Collectors.toMap(p -> p.slotId, p -> p.content, (a, b) -> a));

        List<SlotPromptItem> slotItems = slots.stream().map(slot -> {
            String content = contentBySlotId.getOrDefault(slot.id, "");
            return new SlotPromptItem(slot.id, slot.name, slot.orderIndex, content);
        }).toList();

        // Load images
        List<ImageItem> images = sessionImageRepository.findBySessionId(id).stream()
                .map(img -> new ImageItem(img.id, img.getImageUrl()))
                .toList();

        SessionDetail detail = new SessionDetail(
                session.id,
                session.pipelineId,
                pipeline.name,
                pipeline.worldSetting,
                session.characterId,
                characterName,
                session.status,
                slotItems,
                images,
                session.createdAt
        );

        return Response.ok(detail).build();
    }

    @GET
    @Path("/{id}/images")
    public Response getImages(@PathParam("id") UUID id) {
        List<ImageItem> images = sessionImageRepository.findBySessionId(id).stream()
                .map(img -> new ImageItem(img.id, img.getImageUrl()))
                .toList();
        return Response.ok(images).build();
    }

    @POST
    @Path("/{id}/images")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public Response uploadImage(@PathParam("id") UUID id, @FormParam("file") FileUpload fileUpload) {
        AssembleSessionEntity session = sessionRepository.findByIdOptional(id).orElse(null);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("session 不存在").build();
        }
        if (session.status != SessionStatus.COMPLETED) {
            return Response.status(Response.Status.BAD_REQUEST).entity("session 未完成，不能上传图片").build();
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

        String originalName = fileUpload.fileName();
        if (originalName == null || originalName.isBlank()) {
            originalName = "image";
        }
        String safeName = sanitizeFilename(originalName);
        String fileName = System.currentTimeMillis() + "_" + safeName;

        java.nio.file.Path targetDir = java.nio.file.Path.of(uploadDir).resolve("sessions").resolve(id.toString());
        java.nio.file.Path targetPath = targetDir.resolve(fileName);

        try {
            Files.createDirectories(targetDir);
            Files.copy(fileUpload.filePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("保存文件失败: " + e.getMessage()).build();
        }

        SessionImageEntity image = new SessionImageEntity(id, targetPath.toString());
        sessionImageRepository.persist(image);

        return Response.ok(image.getImageUrl()).build();
    }

    private String sanitizeFilename(String filename) {
        String base = java.nio.file.Path.of(filename).getFileName().toString();
        return base.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
