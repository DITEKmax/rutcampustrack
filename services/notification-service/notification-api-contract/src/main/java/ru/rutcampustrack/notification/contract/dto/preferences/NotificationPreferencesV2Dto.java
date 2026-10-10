package ru.rutcampustrack.notification.contract.dto.preferences;

import java.time.Instant;
import java.util.Map;

public record NotificationPreferencesV2Dto(String revision, boolean splitChannels,
        Map<String, TypePreference> types, Instant mutedUntil, boolean telegramLinked) {
    public record TypePreference(@com.fasterxml.jackson.annotation.JsonProperty(required = true) Boolean appPush,
                                 @com.fasterxml.jackson.annotation.JsonProperty(required = true) Boolean telegram,
                                 @com.fasterxml.jackson.annotation.JsonProperty(required = true) String colorId) {}
    /** Presence is separate from null: an omitted pause must never clear a legacy pause. */
    public static final class Update {
        private String expectedRevision;
        private Boolean splitChannels;
        private Map<String, TypePreference> types;
        private Instant mutedUntil;
        private boolean hasMutedUntil;
        private boolean hasSplitChannels;
        public Update() {}
        public Update(String expectedRevision, boolean splitChannels, Map<String, TypePreference> types) {
            this.expectedRevision = expectedRevision; this.splitChannels = splitChannels; this.types = types; this.hasSplitChannels = true;
        }
        public String expectedRevision() { return expectedRevision; }
        public Boolean splitChannels() { return splitChannels; }
        public Map<String, TypePreference> types() { return types; }
        public Instant mutedUntil() { return mutedUntil; }
        public boolean hasMutedUntil() { return hasMutedUntil; }
        public boolean hasSplitChannels() { return hasSplitChannels; }
        public String getExpectedRevision() { return expectedRevision; }
        public void setExpectedRevision(String value) { expectedRevision = value; }
        public Boolean getSplitChannels() { return splitChannels; }
        public void setSplitChannels(Boolean value) { splitChannels = value; hasSplitChannels = true; }
        public Map<String, TypePreference> getTypes() { return types; }
        public void setTypes(Map<String, TypePreference> value) { types = value; }
        public Instant getMutedUntil() { return mutedUntil; }
        public void setMutedUntil(Instant value) { mutedUntil = value; hasMutedUntil = true; }
    }
}
