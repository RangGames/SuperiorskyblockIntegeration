package wiki.creeper.superiorskyblockIntegeration.client.lang;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;

/**
 * Loads configurable messages from message.yml with sensible defaults.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final Map<String, String> defaults = new HashMap<>();
    private final Map<String, List<String>> defaultLists = new HashMap<>();
    private final String commandLabel;
    private final boolean redisDebug;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
        File file = new File(plugin.getDataFolder(), "message.yml");
        if (!file.exists()) {
            plugin.saveResource("message.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        String label = this.config.getString("general.command-label", defaults.get("general.command-label"));
        if (label == null || label.isBlank()) {
            label = defaults.get("general.command-label");
        }
        this.commandLabel = label;
        this.redisDebug = this.config.getBoolean("logging.redis", false);
    }

    private void registerDefaults() {
        defaults.put("general.command-label", "팜");
        defaults.put("general.player-only", "&c해당 명령은 플레이어만 사용할 수 있습니다.");
        defaults.put("general.unknown-subcommand", "&c알 수 없는 하위 명령입니다: %s");
        defaults.put("general.processing", "&6[Skyblock] 처리 중입니다...");
        defaults.put("general.failure-format", "%s: %s");
        defaults.put("general.error", "&c[Skyblock] 처리 실패: %s");
        defaults.put("general.result-missing", "&c[Skyblock] 처리 실패: 결과가 없습니다.");

        defaults.put("quest.menu-disabled", "&c[Skyblock] 퀘스트 메뉴를 사용할 수 없는 서버입니다.");
        defaults.put("quest.assign-usage", "&e사용법: /%s 퀘스트 발급 <일간|주간> <개수>");
        defaults.put("quest.assign-invalid-type", "&c지원하지 않는 퀘스트 유형입니다. 일간 또는 주간을 입력하세요.");
        defaults.put("quest.assign-invalid-count", "&c퀘스트 개수는 양수여야 합니다.");
        defaults.put("quest.list-usage", "&e사용법: /%s 퀘스트 목록 <일간|주간>");
        defaults.put("quest.list-invalid-type", "&c지원하지 않는 퀘스트 유형입니다. 일간 또는 주간을 입력하세요.");
        defaults.put("quest.subcommands", "&e사용 가능한 하위 명령: 메뉴, 발급, 목록");

        defaults.put("invite.usage", "&e사용법: /%s 초대 <플레이어>");
        defaults.put("invite.sent", "&a[Skyblock] 초대를 전송했습니다.");
        defaults.put("invite.failure-prefix", "[Skyblock] 초대 실패");
        defaults.put("invite.created-target", "&6[Skyblock] %s 님이 섬 초대를 보냈습니다. /%s 초대목록 에서 확인하세요.");
        defaults.put("invite.accepted-notify", "&a[Skyblock] %s 님이 초대를 수락했습니다. /%s 초대목록 에서 확인하세요.");
        defaults.put("invite.rejected-notify", "&e[Skyblock] %s 님이 초대를 거절했습니다. /%s 초대목록 에서 확인하세요.");
        defaults.put("invite.list-header", "&6받은 초대 목록");
        defaults.put("invite.list-empty", "&7받은 초대가 없습니다.");
        defaults.put("invite.list-footer", "&eGUI를 열려면 /%s 초대목록 메뉴 를 사용하세요.");

        defaults.put("invites.failure-prefix", "[Skyblock] 초대 목록 조회 실패");

        defaults.put("accept.success", "&a[Skyblock] 초대를 수락했습니다.");
        defaults.put("accept.failure-prefix", "[Skyblock] 초대 수락 실패");

        defaults.put("deny.success", "&e[Skyblock] 초대를 거절했습니다.");
        defaults.put("deny.failure-prefix", "[Skyblock] 초대 거절 실패");

        defaults.put("members.menu-unavailable", "&c[Skyblock] 구성원 메뉴를 사용할 수 없습니다.");
        defaults.put("members.failure-prefix", "[Skyblock] 멤버 목록 조회 실패");
        defaults.put("members.header", "&6팜 구성원 목록");
        defaults.put("members.empty", "&7등록된 구성원이 없습니다.");
        defaults.put("members.footer", "&eGUI를 열려면 /%s 구성원 메뉴 를 사용하세요.");

        defaults.put("info.cache", "&b[Skyblock] (캐시) 섬 이름: %s");
        defaults.put("info.success", "&b[Skyblock] 섬 정보: %s");
        defaults.put("info.failure-prefix", "[Skyblock] 섬 정보 조회 실패");

        defaults.put("ranking.menu-unavailable", "&c[Skyblock] 순위 메뉴를 사용할 수 없습니다.");
        defaults.put("ranking.contribution-usage", "&e사용법: /%s 순위 기여도 <섬ID>");
        defaults.put("ranking.contribution-failure-prefix", "[Skyblock] 기여도 조회 실패");
        defaults.put("ranking.snapshot-permission", "&c[Skyblock] 해당 명령을 사용할 권한이 없습니다.");
        defaults.put("ranking.snapshot-usage", "&e사용법: /%s 순위 스냅샷 <기간ID> [표시이름] [제한]");
        defaults.put("ranking.snapshot-success", "&a[Skyblock] 랭킹 스냅샷이 생성되었습니다.");
        defaults.put("ranking.snapshot-failure-prefix", "[Skyblock] 스냅샷 생성 실패");
        defaults.put("ranking.page-failure-prefix", "[Skyblock] 순위 목록 조회 실패");
        defaults.put("ranking.subcommands", "&e사용 가능한 하위 명령: 메뉴, 기여도, 스냅샷, 페이지");
        defaults.put("ranking.top-header", "&6팜 순위");
        defaults.put("ranking.top-empty", "&7순위 정보가 없습니다.");
        defaults.put("ranking.top-footer", "&eGUI를 열려면 /%s 순위 메뉴 를 사용하세요.");
        defaults.put("ranking.members-header", "&6팜 기여도");
        defaults.put("ranking.members-empty", "&7기여도 정보가 없습니다.");
        defaults.put("ranking.members-footer", "&eGUI를 열려면 /%s 순위 기여도 [섬ID] 를 사용하세요.");

        defaults.put("history.menu-unavailable", "&c[Skyblock] 히스토리 메뉴를 사용할 수 없습니다.");
        defaults.put("history.page-usage", "&e사용법: /%s 히스토리 페이지 <번호>");
        defaults.put("history.detail-usage", "&e사용법: /%s 히스토리 상세 <기간ID>");
        defaults.put("history.detail-failure-prefix", "[Skyblock] 히스토리 상세 조회 실패");
        defaults.put("history.list-failure-prefix", "[Skyblock] 히스토리 목록 조회 실패");
        defaults.put("history.list-header", "&6히스토리 목록");
        defaults.put("history.list-empty", "&7저장된 히스토리 기록이 없습니다.");
        defaults.put("history.list-footer", "&eGUI를 열려면 /%s 히스토리 메뉴 를 사용하세요.");
        defaults.put("history.subcommands", "&e사용 가능한 하위 명령: 메뉴, 페이지, 상세");
        defaults.put("history.detail-header", "&6[%s 순위 기록]");
        defaults.put("history.detail-empty", "&7기록된 순위가 없습니다.");

        defaults.put("rewards.menu-unavailable", "&c[Skyblock] 보상 메뉴를 사용할 수 없습니다.");
        defaults.put("rewards.failure-prefix", "[Skyblock] 보상 정보를 불러오지 못했습니다");
        defaults.put("rewards.header", "&6팜 순위 보상");
        defaults.put("rewards.empty", "&7등록된 보상 구간이 없습니다.");

        defaults.put("shop.menu-unavailable", "&c[Skyblock] 상점 메뉴를 사용할 수 없습니다.");
        defaults.put("shop.failure-prefix", "[Skyblock] 상점 정보를 불러오지 못했습니다");
        defaults.put("shop.header", "&6팜 상점");
        defaults.put("shop.empty", "&7등록된 상점 아이템이 없습니다.");

        defaults.put("border.menu-unavailable", "&c[Skyblock] 경계선 메뉴를 사용할 수 없습니다.");
        defaults.put("border.toggle-success", "&6[Skyblock] 경계선이 %s되었습니다.");
        defaults.put("border.failure-toggle-prefix", "[Skyblock] 경계선 토글 실패");
        defaults.put("border.color-usage", "&e사용법: /%s 경계 색상 <초록|파랑|빨강>");
        defaults.put("border.color-success", "&6[Skyblock] 경계선 색상이 %s 로 변경되었습니다.");
        defaults.put("border.color-failure-prefix", "[Skyblock] 경계선 색상 변경 실패");
        defaults.put("border.subcommands", "&e사용 가능한 하위 명령: 메뉴, 토글, 색상");

        defaults.put("admin.permission-required", "&c[Skyblock] 관리자 권한이 필요합니다.");
        defaults.put("admin.quest-usage", "&e사용법: /%s 관리자 퀘스트 발급 <일간|주간> <개수>");
        defaults.put("admin.quest-menu-unavailable", "&c[Skyblock] 이 서버에서는 퀘스트 발급 메뉴를 사용할 수 없습니다.");
        defaults.put("admin.quest-invalid-type", "&c지원하지 않는 퀘스트 유형입니다. 일간 또는 주간을 입력하세요.");
        defaults.put("admin.quest-invalid-count", "&c퀘스트 개수는 양수여야 합니다.");
        defaults.put("admin.snapshot-usage", "&e사용법: /%s 관리자 순위 스냅샷 <기간ID> [표시이름] [제한]");
        defaults.put("admin.snapshot-success", "&a[Skyblock] 랭킹 스냅샷이 생성되었습니다.");
        defaults.put("admin.snapshot-failure-prefix", "[Skyblock] 스냅샷 생성 실패");
        defaultLists.put("admin.help-lines", List.of(
                "&6[관리자 명령]",
                "&e/%s 관리자 퀘스트 발급 <일간|주간> <개수> &7- 퀘스트 수동 발급",
                "&e/%s 관리자 순위 스냅샷 <기간ID> [표시이름] [제한] &7- 랭킹 스냅샷 생성"
        ));

        defaults.put("disband.confirm", "&e정말로 섬을 해체하려면 /%s 해체 확인 을 입력하세요.");
        defaults.put("disband.success", "&c[Skyblock] 섬을 해체했습니다.");
        defaults.put("disband.failure-prefix", "[Skyblock] 섬 해체 실패");

        defaults.put("list.header-format", "&6[%s - 페이지 %d/%d]");

        defaultLists.put("help.main", List.of(
                "&e/%s 메뉴 &7- 팜 GUI 열기",
                "&e/%s 퀘스트 <메뉴|발급|목록> &7- 퀘스트 관리",
                "&e/%s 초대 <플레이어> &7- 팜 초대",
                "&e/%s 초대목록 [페이지] &7- 받은 초대 확인",
                "&e/%s 구성원 [페이지] &7- 구성원 목록",
                "&e/%s 순위 [페이지] &7- 팜 순위 보기",
                "&e/%s 히스토리 [페이지] &7- 순위 기록 확인",
                "&e/%s 보상 &7- 순위 보상 확인",
                "&e/%s 상점 &7- 팜 상점 확인",
                "&e/%s 경계 <메뉴|토글|색상> &7- 월드보더 제어",
                "&e/%s 해체 &7- 섬 해체",
                "&e/%s 관리자 &7- 관리자 전용 명령"
        ));
        defaults.put("help.header-title", "/%s 도움말");
        defaults.put("help.empty", "&7표시할 도움말이 없습니다.");
        defaults.put("help.footer", "&e다음 페이지: /%s 도움말 <페이지>");
    }

    public void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(format(key, args));
    }

    public String format(String key, Object... args) {
        String pattern = getString(key);
        if (pattern == null) {
            return ChatColor.RED + key;
        }
        String processed = applyFormat(pattern, args);
        return colorize(processed);
    }

    public List<String> list(String key, Object... args) {
        List<String> raw = getList(key);
        List<String> result = new ArrayList<>(raw.size());
        for (String line : raw) {
            result.add(colorize(applyFormat(line, args)));
        }
        return result;
    }

    private String getString(String key) {
        if (config.contains(key) && config.isString(key)) {
            return config.getString(key);
        }
        return defaults.get(key);
    }

    private List<String> getList(String key) {
        if (config.contains(key)) {
            if (config.isList(key)) {
                List<String> list = config.getStringList(key);
                if (!list.isEmpty()) {
                    return list;
                }
            } else if (config.isString(key)) {
                return List.of(config.getString(key));
            }
        }
        return defaultLists.getOrDefault(key, List.of());
    }

    public String commandLabel() {
        return commandLabel;
    }

    public boolean redisDebugEnabled() {
        return redisDebug;
    }

    private String applyFormat(String template, Object... args) {
        if (template == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (IllegalFormatException ex) {
            plugin.getLogger().warning("Invalid format for message template: " + template + " - " + ex.getMessage());
            return template;
        }
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
