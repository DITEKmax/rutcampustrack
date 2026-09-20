package ru.rutcampustrack.academic.map;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable JDBC-to-service models used by the campus-map read path.
 *
 * <p>The records deliberately do not expose JPA entities.  Byte arrays are
 * copied on construction and access so the bounded asset returned by the
 * repository cannot be changed by a caller while it is being streamed.</p>
 */
public final class CampusMapReadModels {
    private CampusMapReadModels() {
    }

    public record Catalog(long id,
                          long revision,
                          int schemaVersion,
                          int validationPolicyVersion,
                          boolean current) {
    }

    public record Building(long id,
                           String label,
                           int displayOrder,
                           boolean active) {
    }

    public record Floor(long id,
                        long buildingId,
                        String label,
                        int displayOrder,
                        Long currentVersionId,
                        boolean active) {
    }

    public record Plan(long id,
                       long floorId,
                       long version,
                       Long catalogRevisionId,
                       String label,
                       OffsetDateTime publishedAt) {
    }

    public record FormatSlot(long id,
                             long planVersionId,
                             CampusMapFormat format,
                             CampusMapFormatState state,
                             String contentType,
                             Long assetId,
                             long bytes,
                             byte[] sha256,
                             Integer width,
                             Integer height,
                             List<Double> viewBox) {
        public FormatSlot {
            sha256 = sha256 == null ? null : sha256.clone();
            viewBox = viewBox == null ? List.of() : List.copyOf(viewBox);
        }

        @Override
        public byte[] sha256() {
            return sha256 == null ? null : sha256.clone();
        }
    }

    public record AssetMetadata(long id,
                                long planVersionId,
                                CampusMapFormat format,
                                String contentType,
                                long bytes,
                                byte[] sha256,
                                Integer width,
                                Integer height,
                                long contentLength) {
        public AssetMetadata {
            sha256 = sha256 == null ? null : sha256.clone();
        }

        @Override
        public byte[] sha256() {
            return sha256 == null ? null : sha256.clone();
        }
    }

    public record Manifest(int schemaVersion,
                           int validationPolicyVersion,
                           long revision,
                           List<ManifestBuilding> buildings) {
        public Manifest {
            buildings = List.copyOf(buildings);
        }
    }

    /** Public manifest building aggregate assembled from the immutable row models. */
    public record ManifestBuilding(long id,
                                   String label,
                                   List<ManifestFloor> floors) {
        public ManifestBuilding {
            floors = List.copyOf(floors);
        }
    }

    /** Public manifest floor aggregate; {@code plan == null} means no current plan. */
    public record ManifestFloor(long id,
                                String label,
                                ManifestPlan plan) {
    }

    /** Public plan aggregate with the two independent format slots. */
    public record ManifestPlan(long buildingId,
                               long floorId,
                               long version,
                               String label,
                               FormatSlot png,
                               FormatSlot svg) {
    }

    public record ManifestResult(boolean unchanged,
                                 long revision,
                                 Manifest manifest) {
        public ManifestResult {
            if (revision <= 0 || unchanged == (manifest != null)) {
                throw new IllegalArgumentException("invalid campus map manifest result");
            }
        }
    }

    public record PlanResult(ManifestPlan plan) {
        public boolean hasPlan() {
            return plan != null;
        }
    }

    public record Asset(byte[] content) {
        public Asset {
            content = content == null ? null : content.clone();
        }

        @Override
        public byte[] content() {
            return content == null ? null : content.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Asset asset)) {
                return false;
            }
            return Arrays.equals(content, asset.content);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(content);
        }
    }
}
