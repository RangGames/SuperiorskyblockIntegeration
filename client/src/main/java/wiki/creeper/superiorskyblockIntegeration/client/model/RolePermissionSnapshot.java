package wiki.creeper.superiorskyblockIntegeration.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of island role permissions returned by the gateway.
 */
public final class RolePermissionSnapshot {

    private final String islandId;
    private final String islandName;
    private final boolean canManage;
    private final List<Role> roles;

    private RolePermissionSnapshot(String islandId, String islandName, boolean canManage, List<Role> roles) {
        this.islandId = islandId;
        this.islandName = islandName;
        this.canManage = canManage;
        this.roles = roles;
    }

    public static RolePermissionSnapshot from(JsonObject data) {
        if (data == null) {
            return new RolePermissionSnapshot(null, null, false, List.of());
        }
        String islandId = asString(data, "islandId");
        String islandName = asString(data, "islandName");
        boolean canManage = data.has("canManage") && data.get("canManage").getAsBoolean();
        List<Role> roles = new ArrayList<>();
        JsonArray array = data.has("roles") && data.get("roles").isJsonArray()
                ? data.getAsJsonArray("roles")
                : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            Role role = Role.from(element.getAsJsonObject());
            if (role != null) {
                roles.add(role);
            }
        }
        return new RolePermissionSnapshot(islandId, islandName, canManage, Collections.unmodifiableList(roles));
    }

    private static String asString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    public String islandId() {
        return islandId;
    }

    public String islandName() {
        return islandName;
    }

    public boolean canManage() {
        return canManage;
    }

    public List<Role> roles() {
        return roles;
    }

    public Optional<Role> findRole(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (Role role : roles) {
            if (role.name().equalsIgnoreCase(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    public static final class Role {

        private final String name;
        private final String displayName;
        private final int weight;
        private final List<Privilege> privileges;

        private Role(String name, String displayName, int weight, List<Privilege> privileges) {
            this.name = name;
            this.displayName = displayName;
            this.weight = weight;
            this.privileges = privileges;
        }

        private static Role from(JsonObject json) {
            if (json == null || !json.has("name")) {
                return null;
            }
            String name = json.get("name").getAsString();
            String displayName = json.has("displayName") && !json.get("displayName").isJsonNull()
                    ? json.get("displayName").getAsString()
                    : name;
            int weight = json.has("weight") ? json.get("weight").getAsInt() : 0;
            List<Privilege> privileges = new ArrayList<>();
            JsonArray array = json.has("permissions") && json.get("permissions").isJsonArray()
                    ? json.getAsJsonArray("permissions")
                    : new JsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Privilege privilege = Privilege.from(element.getAsJsonObject());
                if (privilege != null) {
                    privileges.add(privilege);
                }
            }
            return new Role(name, displayName, weight, privileges);
        }

        public String name() {
            return name;
        }

        public String displayName() {
            return displayName;
        }

        public int weight() {
            return weight;
        }

        public List<Privilege> privileges() {
            return privileges;
        }

        public long enabledCount() {
            return privileges.stream().filter(Privilege::enabled).count();
        }

        public int totalPrivileges() {
            return privileges.size();
        }
    }

    public static final class Privilege {

        private final String name;
        private boolean enabled;

        private Privilege(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        private static Privilege from(JsonObject json) {
            if (json == null || !json.has("name")) {
                return null;
            }
            String name = json.get("name").getAsString();
            boolean enabled = json.has("enabled") && json.get("enabled").getAsBoolean();
            return new Privilege(name, enabled);
        }

        public String name() {
            return name;
        }

        public boolean enabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
