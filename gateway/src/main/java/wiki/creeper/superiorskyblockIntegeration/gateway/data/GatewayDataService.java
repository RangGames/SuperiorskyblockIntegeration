package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import com.google.gson.JsonObject;

import java.time.Duration;
import java.util.Optional;

import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;
import wiki.creeper.superiorskyblockIntegeration.common.quest.IslandQuestData;

/**
 * Encapsulates gateway data access for shared state and lookups.
 */
public interface GatewayDataService {

    void setData(String namespace, String key, String value, Duration ttl);

    Optional<String> getData(String namespace, String key);

    void deleteData(String namespace, String key);

    void setPlayerProfile(PlayerProfile profile);

    Optional<PlayerProfile> findProfileByName(String name);

    Optional<PlayerProfile> findProfileByUuid(String uuid);

    JsonObject toJson(PlayerProfile profile);

    Optional<IslandQuestData> loadIslandQuests(String islandUuid);

    void saveIslandQuests(IslandQuestData quests);

    void deleteIslandQuests(String islandUuid);
}
