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
import java.util.UUID;

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

        return Response.ok(sessions.stream().map(this::toSessionSummary).toList()).build();
    }

    private SessionSummary toSessionSummary(AssembleSessionEntity session) {
        String characterName = null;
        if (session.characterId != null) {
            characterName = characterRepository.findByIdOptional(session.characterId)
                    .map(c -> c.name).orElse(null);
        }
        int imageCount = (int) sessionImageRepository.findBySessionId(session.id).size();

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
