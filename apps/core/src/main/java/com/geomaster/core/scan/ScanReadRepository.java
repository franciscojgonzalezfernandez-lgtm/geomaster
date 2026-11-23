package com.geomaster.core.scan;

import com.geomaster.core.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Tenant-scoped reads of scans and their progress (REQ-17). Every query filters by the current account. */
@Repository
public class ScanReadRepository {

    private static final String COLUMNS = """
            id, project_id, status, status_reason, trigger, samples, units_total, units_done, units_failed,
            cost_chf, created_at, started_at, finished_at, versions::text AS versions
            """;

    private final JdbcClient db;
    private final JsonMapper json = JsonMapper.builder().build();

    public ScanReadRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<ScanView> find(UUID scanId) {
        return db.sql("SELECT " + COLUMNS + " FROM scan WHERE id = :id AND account_id = :a")
                .param("id", scanId).param("a", TenantContext.accountId())
                .query(this::row).optional()
                .map(r -> r.withEngines(progress(List.of(r.id())).getOrDefault(r.id(), List.of())));
    }

    public boolean projectInAccount(UUID projectId) {
        return db.sql("SELECT EXISTS (SELECT 1 FROM project WHERE id = :p AND account_id = :a)")
                .param("p", projectId).param("a", TenantContext.accountId()).query(Boolean.class).single();
    }

    /** The project's scans, newest first. */
    public List<ScanView> listForProject(UUID projectId, int limit) {
        List<Row> rows = db.sql("SELECT " + COLUMNS + " FROM scan WHERE project_id = :p AND account_id = :a"
                        + " ORDER BY created_at DESC LIMIT :limit")
                .param("p", projectId).param("a", TenantContext.accountId()).param("limit", limit)
                .query(this::row).list();
        Map<UUID, List<ScanView.EngineProgress>> progress = progress(rows.stream().map(Row::id).toList());
        return rows.stream().map(r -> r.withEngines(progress.getOrDefault(r.id(), List.of()))).toList();
    }

    private Map<UUID, List<ScanView.EngineProgress>> progress(List<UUID> scanIds) {
        if (scanIds.isEmpty()) {
            return Map.of();
        }
        record Counts(UUID scan, String engine, int total, int done, int failed, int hits) {}
        List<Counts> counts = db.sql("""
                SELECT scan_id, engine, count(*) AS total,
                       count(*) FILTER (WHERE status = 'done') AS done,
                       count(*) FILTER (WHERE status = 'failed') AS failed,
                       count(*) FILTER (WHERE cache_hit) AS hits
                FROM scan_unit
                WHERE account_id = :a AND scan_id = ANY(CAST(:ids AS uuid[]))
                GROUP BY scan_id, engine
                ORDER BY scan_id, array_position(ARRAY['openai', 'gemini', 'claude'], engine)
                """)
                .param("a", TenantContext.accountId()).param("ids", scanIds.toArray(UUID[]::new))
                .query((rs, i) -> new Counts(rs.getObject("scan_id", UUID.class), rs.getString("engine"),
                        rs.getInt("total"), rs.getInt("done"), rs.getInt("failed"), rs.getInt("hits")))
                .list();
        return counts.stream().collect(Collectors.groupingBy(Counts::scan, Collectors.mapping(
                c -> new ScanView.EngineProgress(c.engine(), null, c.total(), c.done(), c.failed(), c.hits()),
                Collectors.toList())));
    }

    /** A scan row before its per-engine progress is attached; models come from {@code scan.versions}. */
    private record Row(ScanView view, Map<String, String> models) {
        UUID id() {
            return view.id();
        }

        ScanView withEngines(List<ScanView.EngineProgress> engines) {
            List<ScanView.EngineProgress> withModels = new ArrayList<>();
            for (ScanView.EngineProgress e : engines) {
                withModels.add(new ScanView.EngineProgress(e.engine(), models.get(e.engine()),
                        e.total(), e.done(), e.failed(), e.cacheHits()));
            }
            ScanView v = view;
            return new ScanView(v.id(), v.projectId(), v.status(), v.statusReason(), v.trigger(), v.samples(),
                    v.unitsTotal(), v.unitsDone(), v.unitsFailed(), v.costChf(), v.createdAt(), v.startedAt(),
                    v.finishedAt(), List.copyOf(withModels));
        }
    }

    private Row row(ResultSet rs, int i) throws SQLException {
        ScanView v = new ScanView(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("status"),
                rs.getString("status_reason"),
                rs.getString("trigger"),
                rs.getInt("samples"),
                rs.getInt("units_total"),
                rs.getInt("units_done"),
                rs.getInt("units_failed"),
                rs.getBigDecimal("cost_chf"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                List.of());
        return new Row(v, models(rs.getString("versions")));
    }

    private Map<String, String> models(String versions) {
        JsonNode engines = json.readTree(versions).path("engines");
        Map<String, String> out = new HashMap<>();
        for (String engine : engines.propertyNames()) {
            out.put(engine, engines.path(engine).path("model").asString(null));
        }
        return out;
    }
}
