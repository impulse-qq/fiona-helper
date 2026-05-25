package io.promptforge.resource;

import io.promptforge.dto.CharacterResponse;
import io.promptforge.entity.CharacterEntity;
import io.promptforge.repository.CharacterRepository;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/characters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CharacterResource {

    @Inject
    CharacterRepository characterRepository;

    @GET
    public List<CharacterResponse> list() {
        return characterRepository.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") UUID id) {
        CharacterEntity character = characterRepository.findByIdOptional(id).orElse(null);
        if (character == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(toResponse(character)).build();
    }

    @POST
    @Transactional
    public Response create(CharacterRequest request) {
        Response error = validate(request);
        if (error != null) return error;
        CharacterEntity entity = new CharacterEntity();
        entity.name = request.name().trim();
        entity.baseDesign = request.baseDesign();
        entity.personality = request.personality();
        characterRepository.persist(entity);
        return Response.status(Response.Status.CREATED).entity(toResponse(entity)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, CharacterRequest request) {
        CharacterEntity entity = characterRepository.findByIdOptional(id).orElse(null);
        if (entity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response error = validate(request);
        if (error != null) return error;
        entity.name = request.name().trim();
        entity.baseDesign = request.baseDesign();
        entity.personality = request.personality();
        characterRepository.persist(entity);
        return Response.ok(toResponse(entity)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        CharacterEntity entity = characterRepository.findByIdOptional(id).orElse(null);
        if (entity == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        characterRepository.delete(entity);
        return Response.noContent().build();
    }

    private Response validate(CharacterRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("name 不能为空").build();
        }
        if (request.baseDesign() != null && request.baseDesign().length() > 512) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("baseDesign 不能超过 512 字符").build();
        }
        if (request.personality() != null && request.personality().length() > 512) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("personality 不能超过 512 字符").build();
        }
        return null;
    }

    private CharacterResponse toResponse(CharacterEntity entity) {
        String avatarUrl = null;
        if (entity.avatarPath != null && !entity.avatarPath.isBlank()) {
            java.nio.file.Path path = java.nio.file.Path.of(entity.avatarPath).getFileName();
            if (path != null) {
                avatarUrl = "/api/images/" + entity.id + "/" + path.toString();
            }
        }
        return new CharacterResponse(
                entity.id,
                entity.name,
                entity.baseDesign,
                entity.personality,
                avatarUrl
        );
    }

    public record CharacterRequest(String name, String baseDesign, String personality) {}
}
