package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;
import wiki.creeper.superiorskyblockIntegeration.common.quest.IslandQuestData;
import wiki.creeper.superiorskyblockIntegeration.common.quest.IslandQuestEntry;
import wiki.creeper.superiorskyblockIntegeration.common.quest.IslandQuestSet;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestDefinition;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestGenerator;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestRewards;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
import wiki.creeper.superiorskyblockIntegeration.gateway.errors.GatewayException;

/**
 * Encapsulates island quest state management on the gateway.
 */
public final class GatewayQuestService {

    private final GatewayDataService dataService;
    private final GatewayRankingService rankingService;
    private final SuperiorSkyblockBridge bridge;
    private static final String NAMESPACE_FARM_MONEY = "economy.farmMoney";
    private static final String NAMESPACE_VOLUNTEER = "economy.volunteerQuest";
    private static final DateTimeFormatter DAILY_ASSIGNMENT_FORMAT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
    private static final DateTimeFormatter WEEKLY_ASSIGNMENT_FORMAT = DateTimeFormatter.ofPattern("yyyy년 MM월");
    private final ZoneId systemZone = ZoneId.systemDefault();

    public GatewayQuestService(GatewayDataService dataService,
                               GatewayRankingService rankingService,
                               SuperiorSkyblockBridge bridge) {
        this.dataService = Objects.requireNonNull(dataService, "dataService");
        this.rankingService = rankingService;
        this.bridge = bridge;
    }

    public IslandQuestData load(String islandUuid) {
        return dataService.loadIslandQuests(islandUuid)
                .orElseGet(() -> new IslandQuestData(islandUuid,
                        IslandQuestSet.unassigned(QuestType.DAILY),
                        IslandQuestSet.unassigned(QuestType.WEEKLY)));
    }

    public IslandQuestData assign(String islandUuid,
                                  QuestType type,
                                  int questCount,
                                  int memberCount) {
        IslandQuestData current = load(islandUuid);
        IslandQuestSet set = select(current, type);
        if (set.assigned() && !set.quests().isEmpty()) {
            throw new GatewayException(ErrorCode.QUEST_ALREADY_ASSIGNED, "Quest already assigned");
        }
        List<IslandQuestEntry> quests = QuestGenerator.generate(type, questCount, memberCount);
        IslandQuestSet updatedSet = IslandQuestSet.assign(type, questCount, quests);
        IslandQuestData updatedData = apply(current, type, updatedSet);
        dataService.saveIslandQuests(updatedData);
        UUID islandId = parseUuid(islandUuid);
        if (bridge != null && islandId != null) {
            announceAssignment(islandId, type, updatedSet);
        }
        return updatedData;
    }

    public IslandQuestData increment(String islandUuid,
                                     QuestType type,
                                     int questId,
                                     int amount,
                                     UUID contributor) {
        IslandQuestData current = load(islandUuid);
        IslandQuestSet set = select(current, type);
        if (!set.assigned()) {
            throw new GatewayException(ErrorCode.QUEST_NOT_ASSIGNED, "Quest set not assigned");
        }
        IslandQuestEntry entry = set.quest(questId)
                .orElseThrow(() -> new GatewayException(ErrorCode.QUEST_NOT_FOUND, "Quest " + questId + " not found"));
        if (amount <= 0) {
            return current;
        }
        UUID islandId = parseUuid(islandUuid);
        boolean completedBefore = entry.completed();
        IslandQuestEntry updatedEntry = entry.increment(amount, contributor != null ? contributor.toString() : null);
        IslandQuestSet updatedSet = set.replace(updatedEntry);
        if (updatedSet.completed()) {
            updatedSet = updatedSet.withRewardGranted();
        }
        boolean rewardNowGranted = !set.rewardGranted() && updatedSet.rewardGranted();
        IslandQuestData updatedData = apply(current, type, updatedSet);
        dataService.saveIslandQuests(updatedData);
        if (bridge != null && islandId != null) {
            if (!completedBefore && updatedEntry.completed()) {
                announceQuestCompletion(islandId, type, updatedEntry, contributor);
            }
            if (rewardNowGranted) {
                announceAllCompleted(islandId, updatedSet);
            }
        }
        if (rewardNowGranted) {
            grantCompletionRewards(islandUuid, updatedSet);
        }
        if (rankingService != null && contributor != null && amount > 0) {
            rankingService.recordProgress(UUID.fromString(islandUuid), contributor, amount, type);
        }
        return updatedData;
    }

    public JsonObject toJson(IslandQuestData data) {
        JsonObject root = new JsonObject();
        root.addProperty("islandUuid", data.islandUuid());
        root.add("daily", describe(data.daily()));
        root.add("weekly", describe(data.weekly()));
        return root;
    }

    private JsonObject describe(IslandQuestSet set) {
        JsonObject json = new JsonObject();
        json.addProperty("type", set.type().name());
        json.addProperty("assigned", set.assigned());
        if (set.assignedAt() > 0L) {
            json.addProperty("assignedAt", set.assignedAt());
        }
        json.addProperty("questCount", set.questCount());
        json.addProperty("rewardGranted", set.rewardGranted());
        if (set.rewardGrantedAt() > 0L) {
            json.addProperty("rewardGrantedAt", set.rewardGrantedAt());
        }
        json.addProperty("moonlightReward", QuestRewards.moonlightReward(set.type(), set.questCount()));
        json.addProperty("farmPointReward", QuestRewards.farmPointReward(set.type()));

        JsonArray quests = new JsonArray();
        for (IslandQuestEntry entry : set.quests()) {
            JsonObject quest = entry.toJson();
            quest.addProperty("completed", entry.completed());
            quest.addProperty("percentage", entry.target() > 0
                    ? Math.min(100, Math.round((entry.progress() / (double) entry.target()) * 100))
                    : 0);
            QuestDefinition.byId(entry.questId())
                    .ifPresent(definition -> quest.addProperty("displayName", definition.displayName()));
            quests.add(quest);
        }
        json.add("quests", quests);
        return json;
    }

    private IslandQuestSet select(IslandQuestData data, QuestType type) {
        return type.isDaily() ? data.daily() : data.weekly();
    }

    private IslandQuestData apply(IslandQuestData data, QuestType type, IslandQuestSet set) {
        return type.isDaily() ? data.withDaily(set) : data.withWeekly(set);
    }

    private void grantCompletionRewards(String islandUuidRaw, IslandQuestSet set) {
        UUID islandUuid;
        try {
            islandUuid = UUID.fromString(islandUuidRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }
        int questCount = set.questCount();
        int moonlightReward = QuestRewards.moonlightReward(set.type(), questCount);
        int farmPointReward = QuestRewards.farmPointReward(set.type());

        if (moonlightReward > 0) {
            adjustCounter(NAMESPACE_VOLUNTEER, islandUuidRaw, moonlightReward);
        }
        if (farmPointReward > 0) {
            adjustCounter(NAMESPACE_FARM_MONEY, islandUuidRaw, farmPointReward);
            if (rankingService != null) {
                rankingService.awardFarmPoints(islandUuid, farmPointReward, set.type());
            }
        }
    }

    private void announceAssignment(UUID islandUuid, QuestType type, IslandQuestSet set) {
        if (!set.assigned() || bridge == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        String header = "&a&m" + repeat('-', 48) + "&r";
        lines.add(header);
        lines.add(" ");
        ZonedDateTime now = ZonedDateTime.now(systemZone);
        DateTimeFormatter formatter = type.isDaily() ? DAILY_ASSIGNMENT_FORMAT : WEEKLY_ASSIGNMENT_FORMAT;
        String questLabel = type.isDaily() ? "일간" : "주간";
        lines.add("     &6" + now.format(formatter) + "&f의 팜 &c" + questLabel + " 퀘스트 &a" + set.questCount() + "개&f가 발급되었습니다.");
        lines.add(" ");
        for (IslandQuestEntry quest : set.quests()) {
            String name = QuestDefinition.byId(quest.questId())
                    .map(QuestDefinition::displayName)
                    .orElse("퀘스트 " + quest.questId());
            lines.add("     &f* " + name + " &7- &f" + formatNumber(quest.target()) + " 회");
        }
        lines.add(" ");
        lines.add(header);
        broadcast(islandUuid, lines);
    }

    private void announceQuestCompletion(UUID islandUuid,
                                         QuestType type,
                                         IslandQuestEntry entry,
                                         UUID contributor) {
        if (bridge == null || entry == null || !entry.completed()) {
            return;
        }
        String questLabel = type.isDaily() ? "일간" : "주간";
        String questName = QuestDefinition.byId(entry.questId())
                .map(QuestDefinition::displayName)
                .orElse("퀘스트 " + entry.questId());
        String finisher = resolveName(contributor);
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        lines.add("&a[팜] &f" + questLabel + " 퀘스트 &e<" + questName + ">&f을(를) 완료했습니다. &7(" + finisher + ")");
        lines.add("&7 - 목표: &f" + formatNumber(entry.target()) + " 회");
        lines.add(" ");
        broadcast(islandUuid, lines);
    }

    private void announceAllCompleted(UUID islandUuid, IslandQuestSet set) {
        if (bridge == null || set == null || !set.completed()) {
            return;
        }
        int questCount = set.questCount();
        int moonlight = QuestRewards.moonlightReward(set.type(), questCount);
        int farmPoint = QuestRewards.farmPointReward(set.type());
        String questLabel = set.type().isDaily() ? "일간" : "주간";
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        lines.add("&e[팜] &f" + questLabel + " 퀘스트를 모두 완료했습니다!");
        lines.add("&c&l[보상]");
        lines.add("&f - 달빛: &e" + formatNumber(moonlight));
        lines.add("&f - 팜 포인트: &e" + formatNumber(farmPoint));
        lines.add(" ");
        broadcast(islandUuid, lines);
    }

    private void broadcast(UUID islandUuid, List<String> lines) {
        if (bridge == null || islandUuid == null || lines == null || lines.isEmpty()) {
            return;
        }
        bridge.broadcastIslandMessage(islandUuid, lines);
    }

    private void adjustCounter(String namespace, String key, long delta) {
        if (delta == 0L) {
            return;
        }
        long current = dataService.getData(namespace, key)
                .map(this::safeParseLong)
                .orElse(0L);
        long updated = Math.max(0L, current + delta);
        dataService.setData(namespace, key, Long.toString(updated), null);
    }

    private long safeParseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveName(UUID uuid) {
        if (uuid == null || bridge == null) {
            return "누군가";
        }
        return bridge.lookupPlayerName(uuid.toString())
                .filter(name -> !name.isBlank())
                .orElse(uuid.toString().substring(0, 8));
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }

    private String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }
}
