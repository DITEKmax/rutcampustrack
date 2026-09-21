package ru.rutcampustrack.academic.map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.BuildingResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FloorResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FormatState;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanResponse;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Atomic admin commands for campus inventory and immutable floor-plan versions.
 *
 * <p>Upload validation happens before the first write.  A version is then
 * published in one transaction, with absent formats represented by their
 * explicit V26 slot rather than by a missing row.</p>
 */
@Service
public class CampusMapAdminService {
    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("[1-9][0-9]*");
    private static final Pattern UNSAFE_TEXT = Pattern.compile(
            "(?is)(javascript:|data:text/html|expression\\s*\\(|@import|@font-face|url\\s*\\(\\s*(?:https?:|//|file:))");
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final long MAX_BYTES = CampusMapAdminRepository.MAX_ASSET_BYTES;

    private final CampusMapAdminRepository repository;

    public CampusMapAdminService(CampusMapAdminRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> listBuildings() {
        List<CampusMapAdminRepository.FloorRow> floors = repository.findActiveFloors(null);
        Map<Long, PlanResponse> plans = currentPlans(floors);
        return repository.findActiveBuildings().stream()
                .map(building -> new BuildingResponse(
                        decimal(building.id()),
                        building.code(),
                        building.label(),
                        floors.stream()
                                .filter(floor -> floor.buildingId() == building.id())
                                .map(floor -> toFloor(floor, plans.get(floor.id())))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FloorResponse> listFloors(String buildingId) {
        Long parsedBuildingId = buildingId == null || buildingId.isBlank()
                ? null
                : parsePositiveId(buildingId, "buildingId");
        List<CampusMapAdminRepository.FloorRow> floors = repository.findActiveFloors(parsedBuildingId);
        Map<Long, PlanResponse> plans = currentPlans(floors);
        return floors.stream().map(floor -> toFloor(floor, plans.get(floor.id()))).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BuildingResponse createBuilding(CreateBuildingRequest request) {
        String code = requireNumericCode(request.code(), "code");
        if (repository.existsBuildingCode(code)) {
            throw new ConflictException("code", code, "Корпус с таким кодом уже существует");
        }
        String label = defaultLabel(request.label(), "Корпус " + code);
        long id = repository.insertBuilding(code, label, repository.nextBuildingOrder());
        repository.advanceCatalogRevision();
        return new BuildingResponse(decimal(id), code, label, List.of());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FloorResponse createFloor(CreateFloorRequest request) {
        long buildingId = parsePositiveId(request.buildingId(), "buildingId");
        CampusMapAdminRepository.BuildingRow building = repository.findActiveBuilding(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Корпус", "id", buildingId));
        String code = requireNumericCode(request.code(), "code");
        if (repository.existsFloorCode(buildingId, code)) {
            throw new ConflictException("code", code, "Этаж с таким кодом уже существует в корпусе");
        }
        String label = defaultLabel(request.label(), "Этаж " + code);
        long id = repository.insertFloor(buildingId, code, label, repository.nextFloorOrder(buildingId));
        repository.advanceCatalogRevision();
        return new FloorResponse(decimal(id), decimal(building.id()), code, label, null);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public PlanResponse uploadVersion(String floorId,
                                      String label,
                                      MultipartFile png,
                                      MultipartFile svg) {
        long floorKey = parsePositiveId(floorId, "floorId");
        if (isEmpty(png) && isEmpty(svg)) {
            throw new BadRequestException("file", "Загрузите png или svg");
        }
        ValidatedAsset pngAsset = isEmpty(png) ? null : validate(png, CampusMapFormat.PNG);
        ValidatedAsset svgAsset = isEmpty(svg) ? null : validate(svg, CampusMapFormat.SVG);

        CampusMapAdminRepository.FloorRow floor = repository.findFloorForUpdate(floorKey)
                .filter(CampusMapAdminRepository.FloorRow::active)
                .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", floorKey));
        Optional<CampusMapAdminRepository.PlanRow> current = repository.findCurrentPlan(floor.id());
        Map<CampusMapFormat, ValidatedAsset> assets = carryForwardMissingFormats(current, pngAsset, svgAsset);
        long version = current.map(plan -> plan.version() + 1L).orElse(1L);
        if (version <= 0) {
            throw new ConflictException("version", version, "Номер версии схемы исчерпан");
        }

        CampusMapAdminRepository.CatalogRevision revision = repository.advanceCatalogRevision();
        String planLabel = defaultLabel(label, floor.label());
        long planId = repository.insertPlan(floor.id(), version, revision.id(), planLabel);
        for (CampusMapFormat format : CampusMapFormat.values()) {
            ValidatedAsset asset = assets.get(format);
            if (asset == null) {
                repository.insertSlot(planId, format, CampusMapFormatState.ABSENT,
                        expectedMime(format), null, 0, null, null, null);
                continue;
            }
            long assetId = repository.insertAsset(planId, format, asset.contentType(), asset.content(),
                    asset.bytes(), asset.sha256(), asset.width(), asset.height());
            repository.insertSlot(planId, format, CampusMapFormatState.READY, asset.contentType(), assetId,
                    asset.bytes(), asset.sha256(), asset.width(), asset.height());
        }
        repository.publishPlan(planId);
        repository.pointFloorAt(floor.id(), planId);
        return toPlan(repository.findPlan(floor.id(), version)
                        .orElseThrow(() -> new IllegalStateException("published map version disappeared")),
                revision.revision(), repository.findSlots(planId), floor.buildingId());
    }

    @Transactional(readOnly = true)
    public PlanResponse getVersion(String floorId, String version) {
        long floorKey = parsePositiveId(floorId, "floorId");
        long versionKey = parsePositiveId(version, "version");
        CampusMapAdminRepository.FloorRow floor = repository.findActiveFloors(null).stream()
                .filter(row -> row.id() == floorKey)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", floorKey));
        CampusMapAdminRepository.PlanRow plan = repository.findPlan(floor.id(), versionKey)
                .filter(row -> row.publishedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Версия схемы", "version", versionKey));
        long revision = plan.catalogRevisionId() == null
                ? 0
                : catalogRevision(plan.catalogRevisionId());
        return toPlan(plan, revision, repository.findSlots(plan.id()), floor.buildingId());
    }

    @Transactional(readOnly = true)
    public Download downloadAsset(String floorId, String version, String rawFormat, String assetId) {
        long floorKey = parsePositiveId(floorId, "floorId");
        long versionKey = parsePositiveId(version, "version");
        long assetKey = parsePositiveId(assetId, "assetId");
        CampusMapFormat format = parseFormat(rawFormat);
        CampusMapAdminRepository.PlanRow plan = repository.findPlan(floorKey, versionKey)
                .filter(row -> row.publishedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Версия схемы", "version", versionKey));
        CampusMapAdminRepository.SlotRow slot = repository.findSlots(plan.id()).stream()
                .filter(row -> row.format() == format)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Формат схемы", "format", format));
        if (slot.state() != CampusMapFormatState.READY || !Objects.equals(slot.assetId(), assetKey)) {
            throw new ConflictException("assetId", assetId, "Ассет схемы ещё не готов");
        }
        CampusMapAdminRepository.AssetRow asset = repository.findAsset(assetKey, plan.id(), format)
                .orElseThrow(() -> new ResourceNotFoundException("Ассет схемы", "id", assetKey));
        if (asset.content() == null || asset.content().length == 0 || asset.content().length > MAX_BYTES) {
            throw new IllegalStateException("campus map asset is invalid");
        }
        return new Download(asset.contentType(), asset.content(), format);
    }

    private Map<Long, PlanResponse> currentPlans(List<CampusMapAdminRepository.FloorRow> floors) {
        Map<Long, PlanResponse> plans = new java.util.HashMap<>();
        for (CampusMapAdminRepository.FloorRow floor : floors) {
            repository.findCurrentPlan(floor.id()).ifPresent(plan -> {
                if (plan.publishedAt() != null) {
                    long revision = plan.catalogRevisionId() == null ? 0 : catalogRevision(plan.catalogRevisionId());
                    plans.put(floor.id(), toPlan(plan, revision, repository.findSlots(plan.id()), floor.buildingId()));
                }
            });
        }
        return plans;
    }

    private long catalogRevision(long catalogId) {
        Long revision = repositoryCatalogRevision(catalogId);
        if (revision == null || revision <= 0) {
            throw new IllegalStateException("campus map catalog revision is invalid");
        }
        return revision;
    }

    private Long repositoryCatalogRevision(long catalogId) {
        // Kept as a narrow query here so the write repository does not expose
        // the catalog row as a mutable domain object.
        return repository.catalogRevisionValue(catalogId);
    }

    private static FloorResponse toFloor(CampusMapAdminRepository.FloorRow floor, PlanResponse plan) {
        return new FloorResponse(decimal(floor.id()), decimal(floor.buildingId()), floor.code(), floor.label(), plan);
    }

    private static PlanResponse toPlan(CampusMapAdminRepository.PlanRow plan,
                                       long revision,
                                       List<CampusMapAdminRepository.SlotRow> rows,
                                       long buildingId) {
        Map<CampusMapFormat, CampusMapAdminRepository.SlotRow> slots = new EnumMap<>(CampusMapFormat.class);
        for (CampusMapAdminRepository.SlotRow row : rows) {
            slots.put(row.format(), row);
        }
        return new PlanResponse(decimal(buildingId), decimal(plan.floorId()), decimal(plan.version()), plan.label(),
                decimal(revision), slot(slots.get(CampusMapFormat.PNG)), slot(slots.get(CampusMapFormat.SVG)));
    }

    private static CampusMapAdminModels.FormatSlot slot(CampusMapAdminRepository.SlotRow row) {
        if (row == null) {
            throw new IllegalStateException("campus map plan format is missing");
        }
        return new CampusMapAdminModels.FormatSlot(
                row.format() == CampusMapFormat.PNG ? CampusMapAdminModels.Format.png : CampusMapAdminModels.Format.svg,
                switch (row.state()) {
                    case ABSENT -> FormatState.absent;
                    case PROCESSING -> FormatState.processing;
                    case READY -> FormatState.ready;
                    case FAILED -> FormatState.failed;
                },
                row.contentType(),
                row.assetId() == null ? null : decimal(row.assetId()),
                row.bytes(),
                row.sha256() == null ? null : Hex.hex(row.sha256()),
                row.width(),
                row.height());
    }

    private Map<CampusMapFormat, ValidatedAsset> carryForwardMissingFormats(
            Optional<CampusMapAdminRepository.PlanRow> current,
            ValidatedAsset png,
            ValidatedAsset svg) {
        Map<CampusMapFormat, ValidatedAsset> assets = new EnumMap<>(CampusMapFormat.class);
        assets.put(CampusMapFormat.PNG, png);
        assets.put(CampusMapFormat.SVG, svg);
        if (current.isEmpty()) {
            return assets;
        }
        for (CampusMapFormat format : CampusMapFormat.values()) {
            if (assets.get(format) != null) {
                continue;
            }
            repository.findSlots(current.get().id()).stream()
                    .filter(slot -> slot.format() == format && slot.state() == CampusMapFormatState.READY
                            && slot.assetId() != null)
                    .findFirst()
                    .flatMap(slot -> repository.findAsset(slot.assetId(), current.get().id(), format))
                    .map(asset -> new ValidatedAsset(format, asset.contentType(), asset.content(), asset.bytes(),
                            asset.sha256(), asset.width(), asset.height()))
                    .ifPresent(asset -> assets.put(format, asset));
        }
        return assets;
    }

    private ValidatedAsset validate(MultipartFile file, CampusMapFormat format) {
        String expected = expectedMime(format);
        if (!expected.equals(file.getContentType())) {
            throw new BadRequestException("file", "Файл " + format.name().toLowerCase(Locale.ROOT)
                    + " должен иметь MIME " + expected);
        }
        if (file.getSize() <= 0 || file.getSize() > MAX_BYTES) {
            throw new BadRequestException("file", "Размер файла должен быть от 1 байта до 10 MiB");
        }
        final byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException error) {
            throw new BadRequestException("file", "Файл не удалось прочитать");
        }
        if (content.length == 0 || content.length > MAX_BYTES) {
            throw new BadRequestException("file", "Размер файла должен быть от 1 байта до 10 MiB");
        }
        Dimensions dimensions = format == CampusMapFormat.PNG ? validatePng(content) : validateSvg(content);
        return new ValidatedAsset(format, expected, content, content.length, sha256(content),
                dimensions.width(), dimensions.height());
    }

    private static Dimensions validatePng(byte[] content) {
        if (content.length < 24 || !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(content, PNG_SIGNATURE.length))) {
            throw new BadRequestException("file", "PNG-файл имеет неверную сигнатуру");
        }
        ByteBuffer header = ByteBuffer.wrap(content).order(ByteOrder.BIG_ENDIAN);
        int width = header.getInt(16);
        int height = header.getInt(20);
        if (width <= 0 || height <= 0) {
            throw new BadRequestException("file", "PNG-файл должен иметь положительные размеры");
        }
        return new Dimensions(width, height);
    }

    private static Dimensions validateSvg(byte[] content) {
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        if (text.indexOf('\u0000') >= 0 || UNSAFE_TEXT.matcher(text).find()) {
            throw new BadRequestException("file", "SVG содержит запрещённый скрипт, внешний ресурс или шрифт");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content)).getDocumentElement();
            if (root == null || !"svg".equalsIgnoreCase(root.getLocalName() == null ? root.getNodeName() : root.getLocalName())) {
                throw new BadRequestException("file", "SVG должен иметь корневой элемент svg");
            }
            inspectSvg(root);
            Integer width = parseDimension(root.getAttribute("width"));
            Integer height = parseDimension(root.getAttribute("height"));
            String viewBox = root.getAttribute("viewBox");
            if (!viewBox.isBlank() && viewBox.trim().split("[,\\s]+", -1).length != 4) {
                throw new BadRequestException("file", "SVG viewBox должен содержать четыре числа");
            }
            return new Dimensions(width, height);
        } catch (BadRequestException error) {
            throw error;
        } catch (Exception error) {
            throw new BadRequestException("file", "SVG не удалось безопасно разобрать");
        }
    }

    private static void inspectSvg(Element root) {
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            if (node instanceof Element element) {
                String name = (element.getLocalName() == null ? element.getNodeName() : element.getLocalName())
                        .toLowerCase(Locale.ROOT);
                if (SetOfUnsafeElements.contains(name)) {
                    throw new BadRequestException("file", "SVG содержит запрещённый элемент " + name);
                }
                NamedNodeMap attributes = element.getAttributes();
                for (int index = 0; index < attributes.getLength(); index++) {
                    Node attribute = attributes.item(index);
                    String attrName = attribute.getNodeName().toLowerCase(Locale.ROOT);
                    String value = attribute.getNodeValue() == null ? "" : attribute.getNodeValue().trim();
                    if (attrName.startsWith("on")
                            || UNSAFE_TEXT.matcher(value).find()
                            || (attrName.endsWith(":href") || "href".equals(attrName))
                            && !value.isBlank()
                            && !value.startsWith("#")
                            && !value.toLowerCase(Locale.ROOT).startsWith("data:image/png")
                            && !value.toLowerCase(Locale.ROOT).startsWith("data:image/jpeg")) {
                        throw new BadRequestException("file", "SVG содержит внешний или исполняемый ресурс");
                    }
                }
                NodeList children = element.getChildNodes();
                for (int index = 0; index < children.getLength(); index++) {
                    queue.add(children.item(index));
                }
            }
        }
    }

    private static Integer parseDimension(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replaceFirst("(?i)(px|pt|cm|mm|in)$", "");
        try {
            double value = Double.parseDouble(normalized);
            if (!Double.isFinite(value) || value <= 0 || value > Integer.MAX_VALUE) {
                throw new NumberFormatException();
            }
            return (int) Math.round(value);
        } catch (NumberFormatException error) {
            throw new BadRequestException("file", "SVG размеры должны быть положительными числами");
        }
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static boolean isEmpty(MultipartFile file) {
        return file == null || file.isEmpty();
    }

    private static String expectedMime(CampusMapFormat format) {
        return format == CampusMapFormat.PNG ? "image/png" : "image/svg+xml";
    }

    private static CampusMapFormat parseFormat(String raw) {
        if (raw == null) {
            throw new BadRequestException("format", "Формат схемы обязателен");
        }
        try {
            return CampusMapFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new BadRequestException("format", "Поддерживаются только png и svg");
        }
    }

    private static String requireNumericCode(String value, String field) {
        if (value == null || !POSITIVE_DECIMAL.matcher(value.trim()).matches()) {
            throw new BadRequestException(field, "Код должен быть положительным числом");
        }
        return value.trim();
    }

    private static long parsePositiveId(String value, String field) {
        if (value == null || !POSITIVE_DECIMAL.matcher(value).matches()) {
            throw new BadRequestException(field, "Идентификатор должен быть положительным числом");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new BadRequestException(field, "Идентификатор слишком большой");
        }
    }

    private static String defaultLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String decimal(long value) {
        return Long.toString(value);
    }

    public record Download(String contentType, byte[] content, CampusMapFormat format) {
    }

    private record ValidatedAsset(CampusMapFormat format, String contentType, byte[] content, long bytes,
                                  byte[] sha256, Integer width, Integer height) {
    }

    private record Dimensions(Integer width, Integer height) {
    }

    private static final class Hex {
        private Hex() {
        }

        private static String hex(byte[] bytes) {
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(Character.forDigit((value >>> 4) & 0x0F, 16));
                builder.append(Character.forDigit(value & 0x0F, 16));
            }
            return builder.toString();
        }
    }

    private static final class SetOfUnsafeElements {
        private static final java.util.Set<String> values = java.util.Set.of(
                "script", "foreignobject", "iframe", "object", "embed", "audio", "video", "link");

        private static boolean contains(String value) {
            return values.contains(value);
        }

        private SetOfUnsafeElements() {
        }
    }
}
