package wiki.creeper.superiorskyblockIntegeration.common.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates quest rotations using the definitions table and scaling rules from the legacy Skript.
 */
public final class QuestGenerator {

    private QuestGenerator() {
    }

    public static List<IslandQuestEntry> generate(QuestType type, int questCount, int memberCount) {
        if (questCount <= 0) {
            return Collections.emptyList();
        }

        List<QuestDefinition> pool = new ArrayList<>(QuestDefinition.values().length);
        Collections.addAll(pool, QuestDefinition.values());

        List<IslandQuestEntry> result = new ArrayList<>(questCount);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double scale = scalingFactor(type, Math.max(memberCount, 1));

        for (int i = 0; i < questCount && !pool.isEmpty(); i++) {
            int index = random.nextInt(pool.size());
            QuestDefinition definition = pool.remove(index);
            int baseTarget = definition.targetFor(type);
            int adjustedTarget = Math.toIntExact(Math.round(baseTarget * scale));
            result.add(IslandQuestEntry.of(definition.id(), Math.max(1, adjustedTarget)));
        }

        return result;
    }

    private static double scalingFactor(QuestType type, int memberCount) {
        double multiplier = type.isDaily() ? 0.3D : 0.4D;
        return 1.0D + ((Math.max(memberCount, 1) - 1) * multiplier);
    }
}

