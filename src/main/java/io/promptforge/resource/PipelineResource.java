package io.promptforge.resource;

import io.promptforge.dto.*;
import io.promptforge.entity.*;
import io.promptforge.repository.*;
import io.promptforge.service.PipelineAssemblerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/pipelines")
@Produces(MediaType.APPLICATION_JSON)
public class PipelineResource {

    @Inject
    PipelineAssemblerService pipelineService;

    @Inject
    AssembleSessionRepository sessionRepository;

    @Inject
    CharacterRepository characterRepository;

    @Inject
    SessionImageRepository sessionImageRepository;

    @GET
    public List<PipelineSummary> list() {
        return pipelineService.listPipelines(50, 0);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        try {
            PipelineDetail detail = pipelineService.getPipelineDetail(id);
            return Response.ok(detail).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/{id}/sessions")
    public Response getSessions(@PathParam("id") UUID id) {
        List<AssembleSessionEntity> sessions = sessionRepository.find(
                "pipelineId = ?1 AND status = ?2", id, SessionStatus.COMPLETED)
                .list();

        if (sessions.isEmpty()) {
            return Response.ok(List.of()).build();
        }

        // Batch fetch character names (2 queries total instead of N)
        List<UUID> characterIds = sessions.stream()
                .map(s -> s.characterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> characterNameById = characterIds.isEmpty()
                ? Map.of()
                : characterRepository.find("id IN ?1", characterIds).list()
                        .stream()
                        .collect(Collectors.toMap(c -> c.id, c -> c.name));

        // Batch fetch image counts (1 query total instead of N)
        List<UUID> sessionIds = sessions.stream().map(s -> s.id).toList();
        Map<UUID, Integer> imageCountBySession = sessionImageRepository
                .find("sessionId IN ?1", sessionIds).list()
                .stream()
                .collect(Collectors.groupingBy(
                        img -> img.sessionId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return Response.ok(sessions.stream()
                .map(s -> toSessionSummary(s, characterNameById, imageCountBySession))
                .toList()).build();
    }

    private SessionSummary toSessionSummary(AssembleSessionEntity session,
                                            Map<UUID, String> characterNameById,
                                            Map<UUID, Integer> imageCountBySession) {
        String characterName = session.characterId != null
                ? characterNameById.get(session.characterId)
                : null;
        int imageCount = imageCountBySession.getOrDefault(session.id, 0);

        return new SessionSummary(
                session.id,
                session.pipelineId,
                session.characterId,
                characterName,
                session.status,
                session.createdAt,
                imageCount
        );
    }
}
