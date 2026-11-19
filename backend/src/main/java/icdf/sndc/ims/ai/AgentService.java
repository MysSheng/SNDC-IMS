package icdf.sndc.ims.ai;

import icdf.sndc.ims.ai.AgentDtos.AgentFilters;
import icdf.sndc.ims.ai.AgentDtos.AgentRequest;
import icdf.sndc.ims.ai.AgentDtos.AgentResponse;
import icdf.sndc.ims.ai.AgentDtos.AssetBrief;
import icdf.sndc.ims.ai.AgentDtos.ChatTurnDto;
import icdf.sndc.ims.ai.AgentDtos.PendingActionDto;
import icdf.sndc.ims.ai.PendingActionStore.PendingAction;
import icdf.sndc.ims.ai.PendingActionStore.ResolvedChanges;
import icdf.sndc.ims.asset.AssetDtos.AssetQuery;
import icdf.sndc.ims.asset.AssetDtos.AssetRequest;
import icdf.sndc.ims.asset.AssetDtos.AssetResponse;
import icdf.sndc.ims.asset.AssetService;
import icdf.sndc.ims.asset.CountSummary;
import icdf.sndc.ims.asset.DuplicateItem;
import icdf.sndc.ims.asset.ExpiryStatus;
import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.common.PageResponse;
import icdf.sndc.ims.common.RefDto;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.location.LocationService;
import icdf.sndc.ims.manager.ManagerService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 助理：LLM 只產生結構化指令，本類別負責驗證、把名稱解析成實際 ID、查詢資料庫，
 * 並把修改／刪除轉成「待確認動作」。回覆內容一律由資料庫實際結果組成，不由 LLM 撰寫（規格書第 7.5 節）。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    static final int MAX_SEARCH_RESULTS = 20;
    static final int MAX_DELETE_TARGETS = 100;
    static final int MAX_UPDATE_TARGETS = 50;

    private static final String HELP = "我可以幫你查詢資產、統計數量與重複品項，也可以修改或刪除資產（執行前會請你確認）。"
            + "新增資產請使用右上角「CRUD → 新增資產」手動建立。";

    private final LlmClient llm;
    private final ObjectMapper mapper;
    private final AssetService assets;
    private final LocationService locations;
    private final ManagerService managers;
    private final PendingActionStore pending;
    private final Validator validator;
    private final Clock clock;

    public AgentService(LlmClient llm, ObjectMapper mapper, AssetService assets, LocationService locations,
                        ManagerService managers, PendingActionStore pending, Validator validator, Clock clock) {
        this.llm = llm;
        this.mapper = mapper;
        this.assets = assets;
        this.locations = locations;
        this.managers = managers;
        this.pending = pending;
        this.validator = validator;
        this.clock = clock;
    }

    /** 解析失敗時回給使用者的提問（不是例外）。 */
    private static final class Clarify extends RuntimeException {
        Clarify(String question) {
            super(question, null, false, false);
        }
    }

    private record Resolved(AssetQuery query, AgentFilters filters, String description) {}

    // ==================================================================
    // 對話
    // ==================================================================

    public AgentResponse handle(UUID userId, AgentRequest req) {
        LocalDate today = LocalDate.now(clock);
        List<LlmClient.Turn> turns = new ArrayList<>();
        if (req.history() != null) {
            for (ChatTurnDto t : req.history()) {
                turns.add(new LlmClient.Turn(t.role(), t.content()));
            }
        }
        turns.add(new LlmClient.Turn("user", req.message().strip()));

        AgentPlan plan;
        try {
            String system = AgentPrompt.system(today, assets.distinctItemNames());
            plan = mapper.readValue(llm.plan(system, turns), AgentPlan.class);
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("Unparseable LLM output: {}", e.getMessage());
            return AgentResponse.text("CLARIFY", "抱歉，我沒有完全理解你的需求，可以換個說法再說一次嗎？");
        }
        String intent = plan.intent() == null ? "" : plan.intent();
        try {
            return switch (intent) {
                case "SEARCH" -> search(plan);
                case "COUNT" -> count(plan);
                case "DUPLICATES" -> duplicates();
                case "DELETE" -> planDelete(userId, plan);
                case "UPDATE" -> planUpdate(userId, plan);
                case "CLARIFY" -> AgentResponse.text("CLARIFY", blankToDefault(plan.clarificationQuestion(),
                        "可以再說明得更具體一點嗎？例如保管人、存放位置或資產名稱。"));
                default -> AgentResponse.text("UNSUPPORTED", HELP);
            };
        } catch (Clarify c) {
            return AgentResponse.text("CLARIFY", c.getMessage());
        }
    }

    private AgentResponse search(AgentPlan plan) {
        Resolved r = resolve(plan.filters());
        PageResponse<AssetResponse> page = assets.search(r.query(), 0, MAX_SEARCH_RESULTS, null);
        String reply;
        if (page.totalItems() == 0) {
            reply = "沒有找到符合條件的資產" + cond(r) + "。";
        } else {
            reply = "找到 " + page.totalItems() + " 筆符合條件的資產" + cond(r) + "。";
            if (page.totalItems() > MAX_SEARCH_RESULTS) {
                reply += "以下列出前 " + MAX_SEARCH_RESULTS + " 筆，按「在清單中顯示」可查看全部。";
            }
        }
        return new AgentResponse("SEARCH", reply, reply, r.filters(), page.items(), page.totalItems(),
                null, null, null);
    }

    private AgentResponse count(AgentPlan plan) {
        Resolved r = resolve(plan.filters());
        CountSummary s = assets.countSummary(r.query());
        // 有 itemNames 時名稱已列在條件說明中，不重複
        String subject = r.query().itemNames() != null ? "符合條件的資產"
                : r.query().itemName() != null ? "「" + r.query().itemName() + "」"
                : r.query().keyword() != null ? "「" + r.query().keyword() + "」相關的資產" : "符合條件的資產";
        String reply = s.recordCount() == 0
                ? "沒有找到" + subject + cond(r) + "。"
                : subject + "共 " + s.totalQuantity() + " 個，來自 " + s.recordCount() + " 筆紀錄" + cond(r) + "。";
        return new AgentResponse("COUNT", reply, reply, r.filters(), null, null, s, null, null);
    }

    private AgentResponse duplicates() {
        List<DuplicateItem> items = assets.duplicates();
        String reply;
        if (items.isEmpty()) {
            reply = "目前沒有重複的品項（名稱相同且合計數量大於 1）。";
        } else {
            reply = "共有 " + items.size() + " 種品項重複：" + items.stream().limit(10)
                    .map(d -> d.itemName() + " " + d.totalQuantity() + " 個（" + d.recordCount() + " 筆）")
                    .collect(Collectors.joining("、")) + (items.size() > 10 ? "…" : "") + "。";
        }
        return new AgentResponse("DUPLICATES", reply, reply, null, null, null, null, items, null);
    }

    private AgentResponse planDelete(UUID userId, AgentPlan plan) {
        Resolved r = resolve(plan.filters());
        if (!r.query().hasAnyCondition()) {
            throw new Clarify("要刪除哪些資產呢？請說明條件，例如保管人、存放位置或編號。");
        }
        PageResponse<AssetResponse> page = assets.search(r.query(), 0, MAX_DELETE_TARGETS, "assetCode,asc");
        if (page.totalItems() == 0) {
            String reply = "沒有符合條件的資產" + cond(r) + "，所以沒有需要刪除的項目。";
            return new AgentResponse("DELETE", reply, reply, r.filters(), List.of(), 0L, null, null, null);
        }
        if (page.totalItems() > MAX_DELETE_TARGETS) {
            throw new Clarify("符合條件的資產有 " + page.totalItems() + " 筆，超過一次可刪除的上限 "
                    + MAX_DELETE_TARGETS + " 筆，請縮小範圍。");
        }
        List<UUID> ids = page.items().stream().map(AssetResponse::id).toList();
        PendingAction a = pending.create(userId, PendingActionStore.Type.DELETE, ids, null);
        String reply = "請確認是否要刪除以下 " + ids.size() + " 筆資產" + cond(r) + "。";
        PendingActionDto dto = new PendingActionDto(a.id(), "DELETE", reply,
                page.items().stream().map(AssetBrief::from).toList(), Map.of(), a.expiresAt());
        return new AgentResponse("DELETE", reply, reply, r.filters(), page.items(), page.totalItems(), null, null, dto);
    }

    private AgentResponse planUpdate(UUID userId, AgentPlan plan) {
        Resolved r = resolve(plan.filters());
        if (!r.query().hasAnyCondition()) {
            throw new Clarify("要修改哪一筆資產呢？請提供編號或其他條件。");
        }
        if (plan.changes() == null || plan.changes().isEmpty()) {
            throw new Clarify("要把哪個欄位改成什麼呢？例如「名稱改成水龍頭」。");
        }
        Map<String, String> display = new LinkedHashMap<>();
        ResolvedChanges changes = resolveChanges(plan.changes(), display);

        PageResponse<AssetResponse> page = assets.search(r.query(), 0, MAX_UPDATE_TARGETS, "assetCode,asc");
        if (page.totalItems() == 0) {
            String reply = "沒有符合條件的資產" + cond(r) + "，請確認編號或條件是否正確。";
            return new AgentResponse("UPDATE", reply, reply, r.filters(), List.of(), 0L, null, null, null);
        }
        if (page.totalItems() > MAX_UPDATE_TARGETS) {
            throw new Clarify("符合條件的資產有 " + page.totalItems() + " 筆，超過一次可修改的上限 "
                    + MAX_UPDATE_TARGETS + " 筆，請縮小範圍。");
        }
        if (changes.assetCode() != null && page.totalItems() > 1) {
            throw new Clarify("編號必須唯一，一次只能修改一筆資產的編號。符合條件的有 " + page.totalItems() + " 筆，請指定其中一筆。");
        }
        for (AssetResponse a : page.items()) {
            validateMerged(merge(a, changes), a);
        }
        List<UUID> ids = page.items().stream().map(AssetResponse::id).toList();
        PendingAction p = pending.create(userId, PendingActionStore.Type.UPDATE, ids, changes);
        String reply = "請確認是否要修改以下 " + ids.size() + " 筆資產：" + display.entrySet().stream()
                .map(e -> e.getKey() + " → " + e.getValue()).collect(Collectors.joining("、")) + "。";
        PendingActionDto dto = new PendingActionDto(p.id(), "UPDATE", reply,
                page.items().stream().map(AssetBrief::from).toList(), display, p.expiresAt());
        return new AgentResponse("UPDATE", reply, reply, r.filters(), page.items(), page.totalItems(), null, null, dto);
    }

    // ==================================================================
    // 確認／取消
    // ==================================================================

    /** 執行使用者已確認的動作；全部成功才提交，任何一筆失敗則整批不變更。 */
    @Transactional
    public AgentResponse confirm(UUID userId, UUID actionId) {
        PendingAction a = pending.find(actionId, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "ACTION_NOT_FOUND", "The action does not exist, has expired, or belongs to another user"));
        List<AssetResponse> current = new ArrayList<>();
        int missing = 0;
        for (UUID id : a.targetIds()) {
            Optional<AssetResponse> found = assets.findActive(id);
            if (found.isPresent()) {
                current.add(found.get());
            } else {
                missing++;   // 確認前已被其他人刪除
            }
        }
        String reply;
        if (a.type() == PendingActionStore.Type.DELETE) {
            for (AssetResponse r : current) {
                assets.delete(r.id());
            }
            reply = "已刪除 " + current.size() + " 筆資產。";
        } else {
            // 先全部驗證再寫入：任何一筆不符合規則就整批不執行（此時尚未有任何變更）
            List<AssetRequest> merged = new ArrayList<>();
            try {
                for (AssetResponse r : current) {
                    AssetRequest m = merge(r, a.changes());
                    validateMerged(m, r);
                    merged.add(m);
                }
            } catch (Clarify c) {
                pending.remove(actionId);
                return AgentResponse.text("CLARIFY", c.getMessage() + "（此次修改未執行）");
            }
            for (int i = 0; i < current.size(); i++) {
                assets.update(current.get(i).id(), merged.get(i));
            }
            reply = "已修改 " + current.size() + " 筆資產。";
        }
        if (missing > 0) {
            reply += "另有 " + missing + " 筆在確認前已被刪除，已略過。";
        }
        pending.remove(actionId);
        return AgentResponse.text("DONE", reply);
    }

    public void cancel(UUID userId, UUID actionId) {
        pending.find(actionId, userId).ifPresent(a -> pending.remove(a.id()));
    }

    // ==================================================================
    // 驗證與解析
    // ==================================================================

    private Resolved resolve(AgentPlan.Filters f) {
        if (f == null) {
            f = new AgentPlan.Filters(null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null);
        }
        List<String> desc = new ArrayList<>();
        String keyword = TextNormalizer.normalize(f.keyword());
        String assetCode = TextNormalizer.normalize(f.assetCode());
        String itemName = TextNormalizer.normalize(f.itemName());
        String techMission = TextNormalizer.normalize(f.techMission());
        String color = TextNormalizer.normalize(f.color());
        if (keyword != null) desc.add("關鍵字「" + keyword + "」");
        if (assetCode != null) desc.add("編號 " + assetCode);
        if (itemName != null) desc.add("名稱含「" + itemName + "」");
        List<String> itemNames = f.itemNames() == null ? null : f.itemNames().stream()
                .map(TextNormalizer::normalize).filter(java.util.Objects::nonNull).distinct().limit(50).toList();
        if (itemNames != null && itemNames.isEmpty()) itemNames = null;
        if (itemNames != null) desc.add("名稱：" + String.join("、", itemNames));
        if (techMission != null) desc.add("技術團：" + techMission);
        if (color != null) desc.add("顏色：" + color);

        RefDto location = null;
        if (TextNormalizer.normalize(f.locationName()) != null) {
            location = match(f.locationName(), locations.list().stream().map(l -> new RefDto(l.id(), l.name())).toList(), "存放位置");
            desc.add("位置：" + location.name());
        }
        RefDto custodian = null;
        if (TextNormalizer.normalize(f.custodianName()) != null) {
            custodian = match(f.custodianName(), managers.list().stream().map(m -> new RefDto(m.id(), m.displayName())).toList(), "保管人");
            desc.add("保管人：" + custodian.name());
        }
        ExpiryStatus expiry = null;
        if (TextNormalizer.normalize(f.expiryStatus()) != null) {
            try {
                expiry = ExpiryStatus.valueOf(f.expiryStatus().strip());
            } catch (IllegalArgumentException e) {
                throw new Clarify("請問你指的是「已過使用年限」、「90 天內到期」還是「使用年限內」的資產？");
            }
            desc.add(switch (expiry) {
                case EXPIRED -> "已過使用年限";
                case EXPIRING_SOON -> AssetService.EXPIRING_SOON_DAYS + " 天內到期";
                case VALID -> "使用年限內";
            });
        }
        LocalDate purchasedFrom = date(f.purchasedFrom()), purchasedTo = date(f.purchasedTo());
        LocalDate expiresFrom = date(f.expiresFrom()), expiresTo = date(f.expiresTo());
        if (purchasedFrom != null || purchasedTo != null) desc.add("購置日期 " + range(purchasedFrom, purchasedTo));
        if (expiresFrom != null || expiresTo != null) desc.add("到期日 " + range(expiresFrom, expiresTo));
        Integer minQuantity = f.minQuantity();
        if (minQuantity != null) {
            if (minQuantity < 1) throw new Clarify("數量條件需要是 1 以上的整數，可以再確認一下嗎？");
            desc.add("數量 ≥ " + minQuantity);
        }
        Integer year = f.year(), quarter = f.quarter();
        if (quarter != null && year == null) {
            throw new Clarify("請問是哪一年的第 " + quarter + " 季清單？");
        }
        if (year != null && (year < 2000 || year > 2999 || (quarter != null && (quarter < 1 || quarter > 4)))) {
            throw new Clarify("盤點期別需要是 2000–2999 年的第 1–4 季，可以再確認一下嗎？");
        }
        if (year != null) desc.add("盤點期別 " + year + (quarter == null ? " 年" : "Q" + quarter));
        AssetQuery q = new AssetQuery(keyword, assetCode, itemName, techMission, color,
                custodian == null ? null : custodian.id(), location == null ? null : location.id(),
                expiry, purchasedFrom, purchasedTo, expiresFrom, expiresTo, minQuantity, year, quarter, itemNames);
        AgentFilters filters = new AgentFilters(keyword, assetCode, itemName, techMission, color, location, custodian,
                expiry, purchasedFrom, purchasedTo, expiresFrom, expiresTo, minQuantity, year, quarter, itemNames);
        return new Resolved(q, filters, String.join("、", desc));
    }

    private ResolvedChanges resolveChanges(AgentPlan.Changes c, Map<String, String> display) {
        String assetCode = TextNormalizer.normalize(c.assetCode());
        String itemName = TextNormalizer.normalize(c.itemName());
        String techMission = TextNormalizer.normalize(c.techMission());
        String color = TextNormalizer.normalize(c.color());
        String description = TextNormalizer.normalize(c.description());
        String specification = TextNormalizer.normalize(c.specification());
        String unit = TextNormalizer.normalize(c.unit());
        if (assetCode != null) display.put("編號", assetCode);
        if (itemName != null) display.put("名稱", itemName);
        if (techMission != null) display.put("所屬技術團", techMission);
        if (color != null) display.put("顏色", color);
        if (description != null) display.put("備註", description);
        if (specification != null) display.put("規格及摘要", specification);
        if (unit != null) display.put("單位", unit);
        UUID locationId = null;
        if (TextNormalizer.normalize(c.locationName()) != null) {
            RefDto l = match(c.locationName(), locations.list().stream().map(x -> new RefDto(x.id(), x.name())).toList(), "存放位置");
            locationId = l.id();
            display.put("存放位置", l.name());
        }
        UUID managerId = null;
        if (TextNormalizer.normalize(c.custodianName()) != null) {
            RefDto m = match(c.custodianName(), managers.list().stream().map(x -> new RefDto(x.id(), x.displayName())).toList(), "保管人");
            managerId = m.id();
            display.put("保管人", m.name());
        }
        LocalDate purchaseDate = date(c.purchaseDate());
        if (purchaseDate != null) display.put("購置日期", purchaseDate.toString().replace('-', '/'));
        BigDecimal years = c.usefulLifeYears();
        if (years != null) display.put("使用年限", years.stripTrailingZeros().toPlainString() + " 年");
        if (c.quantity() != null) display.put("數量", c.quantity().toString());
        if (display.isEmpty()) {
            throw new Clarify("要把哪個欄位改成什麼呢？例如「名稱改成水龍頭」。");
        }
        return new ResolvedChanges(assetCode, itemName, techMission, color, description, locationId, managerId,
                purchaseDate, years, c.quantity(), specification, unit);
    }

    /** 名稱比對：完全相同 → 唯一部分符合 → 否則請使用者說明（列出候選）。比對在 Backend 進行，名單不送給 LLM。 */
    private RefDto match(String raw, List<RefDto> options, String kind) {
        String name = TextNormalizer.normalize(raw);
        for (RefDto o : options) {
            if (o.name().equals(name)) return o;
        }
        List<RefDto> partial = options.stream()
                .filter(o -> o.name().contains(name) || name.contains(o.name()))
                .toList();
        if (partial.size() == 1) return partial.get(0);
        if (partial.size() > 1) {
            throw new Clarify("「" + name + "」符合多個" + kind + "：" + names(partial) + "，請問是哪一個？");
        }
        throw new Clarify("找不到" + kind + "「" + name + "」。"
                + (options.isEmpty() ? "目前還沒有任何" + kind + "。" : "目前的" + kind + "有：" + names(options) + "。"));
    }

    private static String names(List<RefDto> list) {
        return list.stream().limit(10).map(RefDto::name).collect(Collectors.joining("、")) + (list.size() > 10 ? "…" : "");
    }

    private static LocalDate date(String s) {
        String v = TextNormalizer.normalize(s);
        if (v == null) return null;
        try {
            return LocalDate.parse(v.replace('/', '-'));
        } catch (DateTimeParseException e) {
            throw new Clarify("我無法辨識日期「" + v + "」，可以用「2024/01/15」這樣的格式再說一次嗎？");
        }
    }

    private static String range(LocalDate from, LocalDate to) {
        return (from == null ? "" : from.toString().replace('-', '/')) + "～" + (to == null ? "" : to.toString().replace('-', '/'));
    }

    private static AssetRequest merge(AssetResponse a, ResolvedChanges c) {
        AssetRequest base = AssetService.toRequest(a);
        return new AssetRequest(
                c.assetCode() != null ? c.assetCode() : base.assetCode(),
                c.itemName() != null ? c.itemName() : base.itemName(),
                c.techMission() != null ? c.techMission() : base.techMission(),
                c.managerId() != null ? c.managerId() : base.managerId(),
                c.purchaseDate() != null ? c.purchaseDate() : base.purchaseDate(),
                c.usefulLifeYears() != null ? c.usefulLifeYears() : base.usefulLifeYears(),
                c.locationId() != null ? c.locationId() : base.locationId(),
                c.specification() != null ? c.specification() : base.specification(),
                base.acquisitionType(),
                c.unit() != null ? c.unit() : base.unit(),
                c.quantity() != null ? c.quantity() : base.quantity(),
                base.valueTwd(),
                base.valueUsd(),
                c.color() != null ? c.color() : base.color(),
                c.description() != null ? c.description() : base.description(),
                null,
                null);
    }

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("assetCode", "編號"), Map.entry("itemName", "名稱"), Map.entry("techMission", "所屬技術團"),
            Map.entry("managerId", "保管人"), Map.entry("purchaseDate", "購置日期"),
            Map.entry("usefulLifeYears", "使用年限"), Map.entry("locationId", "存放位置"),
            Map.entry("specification", "規格及摘要"), Map.entry("unit", "單位"), Map.entry("color", "顏色"),
            Map.entry("quantity", "數量"), Map.entry("description", "備註"));

    /** 套用修改後仍須符合手動編輯的所有規則（必填、格式、年份）。 */
    private void validateMerged(AssetRequest m, AssetResponse original) {
        Set<ConstraintViolation<AssetRequest>> violations = validator.validate(m);
        List<String> bad = new ArrayList<>(violations.stream()
                .map(v -> FIELD_LABELS.getOrDefault(v.getPropertyPath().toString(), v.getPropertyPath().toString()))
                .distinct().toList());
        if (m.purchaseDate() != null && (m.purchaseDate().getYear() < 2000 || m.purchaseDate().getYear() > 2999)) {
            bad.add("購置日期（年份須為 2000–2999）");
        }
        if (!bad.isEmpty()) {
            throw new Clarify("無法修改編號 " + original.assetCode() + "：" + String.join("、", bad)
                    + " 不符合規則或尚未填寫。請改用手動編輯補齊資料。");
        }
    }

    private static String cond(Resolved r) {
        return r.description().isEmpty() ? "" : "（" + r.description() + "）";
    }

    private static String blankToDefault(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.strip();
    }
}
