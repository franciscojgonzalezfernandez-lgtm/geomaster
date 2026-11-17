package com.geomaster.core.accounts;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Account-scoped projects (REQ-6..REQ-13). Another account's project is a 404 (REQ-7). */
@RestController
@RequestMapping("/v1/projects")
public class ProjectsController {

    public record CreateProjectRequest(String domain, List<String> languages, String country, String region) {}

    public record ProjectResponse(
            UUID id, String domain, List<String> languages, String country, String region,
            String status, OffsetDateTime createdAt) {
        static ProjectResponse of(ProjectService.ProjectView v) {
            Project p = v.project();
            return new ProjectResponse(p.id(), p.domain(), v.languages(), p.country(), p.region(), p.status(), p.createdAt());
        }
    }

    private final ProjectService service;

    public ProjectsController(ProjectService service) {
        this.service = service;
    }

    /** 201 for a new project; 200 with the existing one when the domain is already there (REQ-10). */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@RequestBody CreateProjectRequest body) {
        CreateProjectRequest b = body == null ? new CreateProjectRequest(null, null, null, null) : body;
        ProjectService.CreateResult r = service.create(b.domain(), b.languages(), b.country(), b.region());
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ProjectResponse.of(r.view()));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return service.list().stream().map(ProjectResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return ProjectResponse.of(service.get(id));
    }
}
