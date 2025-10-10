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
        defaults.put("general.menu-disabled", "&c[Skyblock] 이 서버에서는 해당 메뉴를 사용할 수 없습니다.");

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
        defaults.put("invite.created-target", "&6[Skyblock] %s 님이 팜 초대를 보냈습니다. /%s 초대목록 에서 확인하세요.");
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
        defaults.put("chat.usage", "&e사용법: /%s 채팅 <메시지>");
        defaults.put("chat.empty", "&c전송할 메시지를 입력해주세요.");
        defaults.put("chat.failure-prefix", "[Skyblock] 팜 채팅 실패");
        defaults.put("kick.usage", "&e사용법: /%s 추방 <플레이어> <사유>");
        defaults.put("kick.self", "&c자신을 추방할 수 없습니다.");
        defaults.put("kick.reason-missing", "&c추방 사유를 입력해주세요.");
        defaults.put("kick.success", "&a[Skyblock] %s 님을 추방했습니다.");
        defaults.put("kick.failure-prefix", "[Skyblock] 추방 실패");

        defaults.put("manage.permission-required", "&c[Skyblock] 관리자 전용 명령입니다.");
        defaults.put("manage.reset-usage", "&e사용법: /%s 관리 권한초기화 <닉네임>");
        defaults.put("manage.reset-success", "&a[Skyblock] %s 님의 팜 권한을 초기화했습니다. &7(팜 UUID: %s)");
        defaults.put("manage.reset-failure-prefix", "[Skyblock] 권한 초기화 실패");
        defaults.put("manage.uuid-usage", "&e사용법: /%s 관리 uuid <닉네임>");
        defaults.put("manage.uuid-success", "&b[Skyblock] %s 님의 팜 UUID: %s");
        defaults.put("manage.uuid-failure-prefix", "[Skyblock] 팜 UUID 조회 실패");
        defaults.put("manage.owner-usage", "&e사용법: /%s 관리 주인 <팜UUID>");
        defaults.put("manage.owner-success", "&b[Skyblock] 팜 소유자: %s (%s)");
        defaults.put("manage.owner-failure-prefix", "[Skyblock] 팜 소유자 조회 실패");
        defaults.put("manage.gambling-usage", "&e사용법: /%s 관리 도박장 <닉네임>");
        defaults.put("manage.gambling-enabled", "&a[Skyblock] %s 님의 도박장을 활성화했습니다.");
        defaults.put("manage.gambling-disabled", "&e[Skyblock] %s 님의 도박장을 비활성화했습니다.");
        defaults.put("manage.gambling-failure-prefix", "[Skyblock] 도박장 상태 변경 실패");
        defaults.put("manage.menu-disabled", "&c[Skyblock] GUI 기능을 사용할 수 없는 서버입니다.");
        defaults.put("manage.power-usage", "&e사용법: /%s 관리 점수보상 <티어>");
        defaults.put("manage.power-invalid-tier", "&c지원하지 않는 보상 티어입니다. (5000, 10000, 50000, 100000, 500000, 750000, 1000000, 3000000, 5000000, 10000000)");
        defaults.put("manage.top-usage", "&e사용법: /%s 관리 순위보상 <순위>");
        defaults.put("manage.top-invalid-rank", "&c순위는 1 이상의 숫자여야 합니다.");
        defaults.put("manage.top-give-success", "&a[Skyblock] %d위 보상을 지급했습니다. 총 %d개, 떨어진 아이템 %d개.");
        defaults.put("manage.top-give-empty", "&e[Skyblock] 해당 순위에 등록된 보상이 없습니다.");
        defaults.put("manage.top-give-failure-prefix", "[Skyblock] 순위 보상 지급 실패");
        defaults.put("manage.unknown-subcommand", "&c알 수 없는 관리 하위 명령입니다: %s");
        defaultLists.put("manage.help-lines", List.of(
                "&6[팜 관리 명령]",
                "&e/%s 관리 권한초기화 <닉네임> &7- 해당 팜 권한을 기본값으로 되돌립니다.",
                "&e/%s 관리 uuid <닉네임> &7- 플레이어가 속한 팜 UUID를 조회합니다.",
                "&e/%s 관리 주인 <팜UUID> &7- 팜 소유자를 확인합니다.",
                "&e/%s 관리 도박장 <닉네임> &7- 해당 팜의 도박 기능을 켜고 끕니다.",
                "&e/%s 관리 점수보상 <티어> &7- 해당 티어의 점수 보상을 편집합니다.",
                "&e/%s 관리 순위보상 <순위> &7- 해당 순위의 보상을 편집합니다.",
                "&e/%s 관리 순위받기 <순위> &7- 순위 보상을 수령합니다."
        ));

        defaults.put("info.cache", "&b[Skyblock] (캐시) 팜 이름: %s");
        defaults.put("info.success", "&b[Skyblock] 팜 정보: %s");
        defaults.put("info.failure-prefix", "[Skyblock] 팜 정보 조회 실패");

        defaults.put("points.failure-prefix", "[Skyblock] 점수 정보를 불러오지 못했습니다");
        defaults.put("points.no-island", "&c[Skyblock] 가입된 팜이 없습니다.");
        defaults.put("points.header", "&6[Skyblock] %s 팜의 팜 점수");
        defaults.put("points.total", "&f· 현재 팜 점수: &e%s점");
        defaults.put("points.daily", "&f· 일일 팜 점수: &b%s점");
        defaults.put("points.weekly", "&f· 주간 팜 점수: &a%s점");

        defaults.put("hopper.failure-prefix", "[Skyblock] 호퍼 정보를 불러오지 못했습니다");
        defaults.put("hopper.no-island", "&c[Skyblock] 가입된 팜이 없습니다.");
        defaults.put("hopper.summary", "&f현재 설치된 호퍼: &e%s개 &7/ 최대 &b%s개");

        defaults.put("rating.failure-prefix", "[Skyblock] 평가 처리 실패");
        defaults.put("rating.usage", "&e사용법: /%s 평가 <0~5>");
        defaults.put("rating.invalid-number", "&c점수는 숫자로 입력해주세요.");
        defaults.put("rating.invalid-range", "&c점수는 0부터 5 사이로 입력해주세요.");
        defaults.put("rating.cleared", "&a[Skyblock] 해당 팜 평가를 삭제했습니다.");
        defaults.put("rating.success", "&a[Skyblock] 해당 팜을 %s점으로 평가했습니다.");
        defaults.put("rating.previous", "&7이전 평가: %s점");

        defaults.put("rules.failure-prefix", "[Skyblock] 팜 규칙 작업 실패");
        defaults.put("rules.header", "&6[팜 규칙] &f%s &7(%d/%d)");
        defaults.put("rules.empty", "&7등록된 팜 규칙이 없습니다.");
        defaults.put("rules.line", "&e%d. &f%s");
        defaults.put("rules.add-usage", "&e사용법: /%s 규칙 추가 <내용>");
        defaults.put("rules.remove-usage", "&e사용법: /%s 규칙 삭제 <번호>");
        defaults.put("rules.subcommands", "&e사용 가능한 하위 명령: 규칙 목록, 규칙 추가, 규칙 삭제");
        defaults.put("rules.add-success", "&a[Skyblock] 규칙이 추가되었습니다: %s");
        defaults.put("rules.remove-success", "&a[Skyblock] %d번 규칙을 삭제했습니다: %s");
        defaults.put("rules.edit-hint", "&7규칙은 최대 %d개까지 등록할 수 있습니다. /%s 규칙 <추가|삭제>");

        defaults.put("ranking.menu-unavailable", "&c[Skyblock] 순위 메뉴를 사용할 수 없습니다.");
        defaults.put("ranking.contribution-usage", "&e사용법: /%s 순위 기여도 <팜ID>");
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
        defaults.put("ranking.members-footer", "&eGUI를 열려면 /%s 순위 기여도 [팜ID] 를 사용하세요.");

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

        defaults.put("disband.confirm", "&e정말로 팜을 해체하려면 /%s 해체 확인 을 입력하세요.");
        defaults.put("disband.success", "&c[Skyblock] 팜을 해체했습니다.");
        defaults.put("disband.failure-prefix", "[Skyblock] 팜 해체 실패");

        defaults.put("list.header-format", "&6[%s - 페이지 %d/%d]");

        defaultLists.put("help.main", List.of(
                "&6■ 기본",
                "&e/%s 메뉴 &7- 통합 팜 관리 허브를 엽니다.",
                "&e/%s 도움말 [페이지] &7- 사용 가능한 명령과 설명을 확인합니다.",
                "&e/%s 퀘스트 <메뉴|발급|목록> &7- 일간/주간 퀘스트를 발급하고 확인합니다.",
                "&6■ 구성원 & 초대",
                "&e/%s 초대 <플레이어> &7- 팜으로 초대합니다.",
                "&e/%s 초대목록 [페이지|메뉴] &7- 받은 초대를 확인하고 처리합니다.",
                "&e/%s 수락 [초대ID] &7- 초대를 수락하고 팜으로 이동합니다.",
                "&e/%s 거절 [초대ID] &7- 초대를 거절합니다.",
                "&e/%s 구성원 [페이지|메뉴] &7- 구성원 목록과 상태를 확인합니다.",
                "&e/%s 추방 <닉네임> <사유> &7- 구성원을 추방합니다.",
                "&e/%s 알바 <목록|추가|삭제> &7- 팜 알바 인원을 관리합니다.",
                "&e/%s 차단 <목록|추가|삭제> &7- 방문 차단 명단을 관리합니다.",
                "&6■ 정보 & 관리",
                "&e/%s 정보 [닉네임] &7- 팜 요약 정보를 조회합니다.",
                "&e/%s 포인트 &7- 현재 팜 점수(총/일/주)를 확인합니다.",
                "&e/%s 호퍼 &7- 설치된 팜 호퍼 수와 제한을 확인합니다.",
                "&e/%s 평가 <0~5> &7- 방문한 팜을 평가하거나 초기화합니다.",
                "&e/%s 규칙 <목록|추가|삭제> &7- 팜 규칙을 관리합니다.",
                "&e/%s 권한 <메뉴|목록|설정> &7- 역할별 권한을 확인하고 변경합니다.",
                "&e/%s 금고 <정보|입금|출금|기록|잠금> &7- 팜 금고를 관리합니다.",
                "&e/%s 보상 &7- 순위별 지급 팜 포인트를 확인합니다.",
                "&e/%s 상점 &7- 팜 포인트 전용 상점을 엽니다.",
                "&6■ 순위 & 기록",
                "&e/%s 순위 <메뉴|기여도|스냅샷|페이지> &7- 팜 순위와 기여도를 조회합니다.",
                "&e/%s 히스토리 <메뉴|페이지|상세> &7- 시즌별 순위 기록을 확인합니다.",
                "&6■ 이동 & 환경",
                "&e/%s 홈 [설정] &7- 등록된 팜 홈으로 이동하거나 생성합니다.",
                "&e/%s sethome &7- 현재 위치를 팜 홈으로 설정합니다.",
                "&e/%s 워프 <메뉴|페이지|닉네임> &7- 공개된 다른 팜으로 이동합니다.",
                "&e/%s 경계 <메뉴|토글|색상> &7- 팜 월드보더를 제어합니다.",
                "&6■ 운영 도구",
                "&e/%s 해체 [확인] &7- 팜을 완전히 삭제합니다.",
                "&e/%s 관리 <도움말|...> &7- 운영진용 관리 명령을 실행합니다.",
                "&e/%s 관리자 &7- 관리자 전용 GUI와 도구를 엽니다.",
                "&7원본 SuperiorSkyblock 명령(/is go 등)도 그대로 사용할 수 있습니다."
        ));
        defaults.put("help.header-title", "/%s 도움말");
        defaults.put("help.empty", "&7표시할 도움말이 없습니다.");
        defaults.put("help.footer", "&e다음 페이지: /%s 도움말 <페이지>");

        defaults.put("permissions.failure-prefix", "[Skyblock] 권한 정보 조회 실패");
        defaults.put("permissions.subcommands", "&e사용 가능한 하위 명령: 메뉴, 목록 [역할], 설정 <역할> <권한> <허용|차단>");
        defaults.put("permissions.update-usage", "&e사용법: /%s 권한 설정 <역할> <권한> <허용|차단>");
        defaults.put("permissions.invalid-role", "&c알 수 없는 역할입니다: %s");
        defaults.put("permissions.invalid-privilege", "&c알 수 없는 권한입니다: %s");
        defaults.put("permissions.invalid-state", "&c상태는 허용 또는 차단만 사용할 수 있습니다.");
        defaults.put("permissions.update-success", "&a[Skyblock] %s 역할의 %s 권한을 %s 처리했습니다.");
        defaultLists.put("permissions.list-header", List.of(
                "&6[팜 역할 권한 요약]"
        ));
        defaults.put("permissions.list-line", "&e%s &7- 허용 %d / %d");
        defaults.put("permissions.list-footer", "&e상세 보기: /%s 권한 목록 <역할>");
        defaults.put("permissions.list-role-header", "&6[%s 역할 권한] (%d/%d)");
        defaults.put("permissions.list-role-line", "&7- %s: %s");
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
