package ru.rutcampustrack.academic.map;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.BuildingResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FloorResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FormatState;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanResponse;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanStatus;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;

/**
 * Atomic admin commands for campus inventory and immutable floor-plan versions.
 *
 * <p>Upload validation happens before the first write. A version is then published in one
 * transaction, with absent formats represented by their explicit V26 slot rather than by a missing
 * row.
 */
@Service
public class CampusMapAdminService {
    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("[1-9][0-9]*");
    private static final Pattern UNSAFE_TEXT =
            Pattern.compile(
                    "(?is)(javascript:|data:text/html|expression\\s*\\(|@import|@font-face|url\\s*\\(\\s*(?:https?:|//|file:))");
    private static final Pattern CSS_RESOURCE =
            Pattern.compile("(?i)(?:url|src|image-set|-webkit-image-set|image)\\s*\\(");
    private static final byte[] PNG_SIGNATURE =
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final long MAX_BYTES = CampusMapAdminRepository.MAX_ASSET_BYTES;

    private final CampusMapAdminRepository repository;
    private final CampusMapUsageService usage;

    public CampusMapAdminService(CampusMapAdminRepository repository) {
        this(repository, null);
    }

    @Autowired
    public CampusMapAdminService(CampusMapAdminRepository repository, CampusMapUsageService usage) {
        this.repository = repository;
        this.usage = usage;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CampusMapAdminModels.PlanTargetResponse getPlanTarget(
            String buildingCode, String floorCode) {
        String building = requireUploadCode(buildingCode, "buildingCode");
        String floorCodeValue = requireUploadCode(floorCode, "floorCode");
        var target = repository.findActiveFloorByCodes(building, floorCodeValue);
        if (target.isEmpty()) {
            return new CampusMapAdminModels.PlanTargetResponse(
                    building, floorCodeValue, CampusMapAdminModels.PlanTargetStatus.absent, null);
        }
        var floor = target.get();
        PlanResponse plan = currentPlans(List.of(floor)).get(floor.id());
        long count = usage == null ? 0L : usage.allTimeFloorCounts().getOrDefault(floor.id(), 0L);
        return new CampusMapAdminModels.PlanTargetResponse(
                building,
                floorCodeValue,
                CampusMapAdminModels.PlanTargetStatus.found,
                toFloor(floor, plan, count));
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> listBuildings() {
        List<CampusMapAdminRepository.FloorRow> floors = repository.findActiveFloors(null);
        Map<Long, PlanResponse> plans = currentPlans(floors);
        Map<Long, Long> openCounts = usage == null ? Map.of() : usage.allTimeFloorCounts();
        return repository.findActiveBuildings().stream()
                .map(
                        building ->
                                new BuildingResponse(
                                        decimal(building.id()),
                                        building.code(),
                                        building.label(),
                                        floors.stream()
                                                .filter(
                                                        floor ->
                                                                floor.buildingId() == building.id())
                                                .map(
                                                        floor ->
                                                                toFloor(
                                                                        floor,
                                                                        plans.get(floor.id()),
                                                                        openCounts.getOrDefault(
                                                                                floor.id(), 0L)))
                                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FloorResponse> listFloors(String buildingId) {
        Long parsedBuildingId =
                buildingId == null || buildingId.isBlank()
                        ? null
                        : parsePositiveId(buildingId, "buildingId");
        List<CampusMapAdminRepository.FloorRow> floors =
                repository.findActiveFloors(parsedBuildingId);
        Map<Long, PlanResponse> plans = currentPlans(floors);
        Map<Long, Long> openCounts = usage == null ? Map.of() : usage.allTimeFloorCounts();
        return floors.stream()
                .map(
                        floor ->
                                toFloor(
                                        floor,
                                        plans.get(floor.id()),
                                        openCounts.getOrDefault(floor.id(), 0L)))
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BuildingResponse createBuilding(CreateBuildingRequest request) {
        String code = requireNumericCode(request.code(), "code");
        repository.lockCatalogForWrite();
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
        repository.lockCatalogForWrite();
        CampusMapAdminRepository.BuildingRow building =
                repository
                        .findActiveBuilding(buildingId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Корпус", "id", buildingId));
        String code = requireNumericCode(request.code(), "code");
        if (repository.existsFloorCode(buildingId, code)) {
            throw new ConflictException(
                    "code", code, "Этаж с таким кодом уже существует в корпусе");
        }
        String label = defaultLabel(request.label(), "Этаж " + code);
        long id =
                repository.insertFloor(
                        buildingId, code, label, repository.nextFloorOrder(buildingId));
        repository.advanceCatalogRevision();
        return new FloorResponse(decimal(id), decimal(building.id()), code, label, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UploadPlanResponse uploadPlan(
            String buildingCode,
            String floorCode,
            String label,
            MultipartFile png,
            MultipartFile svg) {
        String buildingNumber = requireUploadCode(buildingCode, "buildingCode");
        String floorNumber = requireUploadCode(floorCode, "floorCode");
        if (label != null && label.length() > 255) {
            throw new BadRequestException(
                    "label", "Название схемы не должно превышать 255 символов");
        }
        if (isEmpty(png) && isEmpty(svg)) {
            throw new BadRequestException("file", "Загрузите png или svg");
        }
        ValidatedAsset pngAsset = isEmpty(png) ? null : validate(png, CampusMapFormat.PNG);
        ValidatedAsset svgAsset = isEmpty(svg) ? null : validate(svg, CampusMapFormat.SVG);

        repository.lockCatalogForWrite();
        Optional<CampusMapAdminRepository.BuildingRow> existingBuilding =
                repository.findBuildingByCode(buildingNumber);
        existingBuilding
                .filter(building -> !building.active())
                .ifPresent(
                        building -> {
                            throw new ConflictException(
                                    "buildingCode",
                                    buildingNumber,
                                    "Корпус с таким кодом неактивен");
                        });
        long buildingId =
                existingBuilding
                        .map(CampusMapAdminRepository.BuildingRow::id)
                        .orElseGet(
                                () ->
                                        repository.insertBuilding(
                                                buildingNumber,
                                                "Корпус " + buildingNumber,
                                                repository.nextBuildingOrder()));
        Optional<CampusMapAdminRepository.FloorRow> existingFloor =
                repository.findFloorByCodeForUpdate(buildingId, floorNumber);
        existingFloor
                .filter(floor -> !floor.active())
                .ifPresent(
                        floor -> {
                            throw new ConflictException(
                                    "floorCode", floorNumber, "Этаж с таким кодом неактивен");
                        });
        CampusMapAdminRepository.FloorRow floor =
                existingFloor.orElseGet(
                        () -> {
                            String floorLabel = "Этаж " + floorNumber;
                            int order = repository.nextFloorOrder(buildingId);
                            long floorId =
                                    repository.insertFloor(
                                            buildingId, floorNumber, floorLabel, order);
                            return new CampusMapAdminRepository.FloorRow(
                                    floorId,
                                    buildingId,
                                    floorNumber,
                                    floorLabel,
                                    order,
                                    null,
                                    true);
                        });
        Optional<CampusMapAdminRepository.PlanRow> current = repository.findCurrentPlan(floor.id());
        PlanResponse plan = publishValidatedVersion(floor, label, pngAsset, svgAsset, current);
        return new UploadPlanResponse(
                current.isEmpty() ? UploadPlanStatus.created : UploadPlanStatus.replaced,
                existingBuilding.isEmpty(),
                existingFloor.isEmpty(),
                plan);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanResponse uploadVersion(
            String floorId, String label, MultipartFile png, MultipartFile svg) {
        long floorKey = parsePositiveId(floorId, "floorId");
        if (isEmpty(png) && isEmpty(svg)) {
            throw new BadRequestException("file", "Загрузите png или svg");
        }
        ValidatedAsset pngAsset = isEmpty(png) ? null : validate(png, CampusMapFormat.PNG);
        ValidatedAsset svgAsset = isEmpty(svg) ? null : validate(svg, CampusMapFormat.SVG);

        repository.lockCatalogForWrite();
        CampusMapAdminRepository.FloorRow floor =
                repository
                        .findFloorForUpdate(floorKey)
                        .filter(CampusMapAdminRepository.FloorRow::active)
                        .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", floorKey));
        Optional<CampusMapAdminRepository.PlanRow> current = repository.findCurrentPlan(floor.id());
        return publishValidatedVersion(floor, label, pngAsset, svgAsset, current);
    }

    private PlanResponse publishValidatedVersion(
            CampusMapAdminRepository.FloorRow floor,
            String label,
            ValidatedAsset pngAsset,
            ValidatedAsset svgAsset,
            Optional<CampusMapAdminRepository.PlanRow> current) {
        Map<CampusMapFormat, ValidatedAsset> assets =
                carryForwardMissingFormats(current, pngAsset, svgAsset);
        long version = repository.highestPlanVersion(floor.id()) + 1L;
        if (version <= 0) {
            throw new ConflictException("version", version, "Номер версии схемы исчерпан");
        }

        CampusMapAdminRepository.CatalogRevision revision = repository.advanceCatalogRevision();
        String planLabel = defaultLabel(label, floor.label());
        long planId = repository.insertPlan(floor.id(), version, revision.id(), planLabel);
        for (CampusMapFormat format : CampusMapFormat.values()) {
            ValidatedAsset asset = assets.get(format);
            if (asset == null) {
                repository.insertSlot(
                        planId,
                        format,
                        CampusMapFormatState.ABSENT,
                        expectedMime(format),
                        null,
                        0,
                        null,
                        null,
                        null);
                continue;
            }
            long assetId =
                    repository.insertAsset(
                            planId,
                            format,
                            asset.contentType(),
                            asset.content(),
                            asset.bytes(),
                            asset.sha256(),
                            asset.width(),
                            asset.height());
            repository.insertSlot(
                    planId,
                    format,
                    CampusMapFormatState.READY,
                    asset.contentType(),
                    assetId,
                    asset.bytes(),
                    asset.sha256(),
                    asset.width(),
                    asset.height());
        }
        repository.publishPlan(planId);
        repository.pointFloorAt(floor.id(), planId);
        return toPlan(
                repository
                        .findPlan(floor.id(), version)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "published map version disappeared")),
                revision.revision(),
                repository.findSlots(planId),
                floor.buildingId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BuildingResponse updateBuilding(String buildingId, UpdateInventoryRequest request) {
        long id = parsePositiveId(buildingId, "buildingId");
        String code = requireNumericCode(request.code(), "code");
        String label = defaultLabel(request.label(), "Корпус " + code);
        repository.lockCatalogForWrite();
        CampusMapAdminRepository.BuildingRow building =
                repository
                        .findActiveBuilding(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Корпус", "id", id));
        if (!building.code().equals(code) && repository.existsBuildingCode(code)) {
            throw new ConflictException("code", code, "Корпус с таким кодом уже существует");
        }
        if (!building.code().equals(code) || !building.label().equals(label)) {
            repository.updateBuilding(id, code, label);
            repository.advanceCatalogRevision();
        }
        List<FloorResponse> floors = listFloors(decimal(id));
        return new BuildingResponse(decimal(id), code, label, floors);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FloorResponse updateFloor(String floorId, UpdateInventoryRequest request) {
        long id = parsePositiveId(floorId, "floorId");
        String code = requireNumericCode(request.code(), "code");
        String label = defaultLabel(request.label(), "Этаж " + code);
        repository.lockCatalogForWrite();
        CampusMapAdminRepository.FloorRow floor =
                repository
                        .findFloorForUpdate(id)
                        .filter(CampusMapAdminRepository.FloorRow::active)
                        .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", id));
        repository
                .findActiveBuilding(floor.buildingId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Корпус", "id", floor.buildingId()));
        if (!floor.code().equals(code) && repository.existsFloorCode(floor.buildingId(), code)) {
            throw new ConflictException(
                    "code", code, "Этаж с таким кодом уже существует в корпусе");
        }
        if (!floor.code().equals(code) || !floor.label().equals(label)) {
            repository.updateFloor(id, code, label);
            repository.advanceCatalogRevision();
        }
        return listFloors(decimal(floor.buildingId())).stream()
                .filter(row -> row.id().equals(decimal(id)))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("updated campus map floor disappeared"));
    }

    @Transactional(readOnly = true)
    public PlanResponse getVersion(String floorId, String version) {
        long floorKey = parsePositiveId(floorId, "floorId");
        long versionKey = parsePositiveId(version, "version");
        CampusMapAdminRepository.FloorRow floor =
                repository.findActiveFloors(null).stream()
                        .filter(row -> row.id() == floorKey)
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Этаж", "id", floorKey));
        CampusMapAdminRepository.PlanRow plan =
                repository
                        .findPlan(floor.id(), versionKey)
                        .filter(row -> row.publishedAt() != null)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Версия схемы", "version", versionKey));
        long revision =
                plan.catalogRevisionId() == null ? 0 : catalogRevision(plan.catalogRevisionId());
        return toPlan(plan, revision, repository.findSlots(plan.id()), floor.buildingId());
    }

    @Transactional(readOnly = true)
    public Download downloadAsset(
            String floorId, String version, String rawFormat, String assetId) {
        long floorKey = parsePositiveId(floorId, "floorId");
        long versionKey = parsePositiveId(version, "version");
        long assetKey = parsePositiveId(assetId, "assetId");
        CampusMapFormat format = parseFormat(rawFormat);
        CampusMapAdminRepository.PlanRow plan =
                repository
                        .findPlan(floorKey, versionKey)
                        .filter(row -> row.publishedAt() != null)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Версия схемы", "version", versionKey));
        CampusMapAdminRepository.SlotRow slot =
                repository.findSlots(plan.id()).stream()
                        .filter(row -> row.format() == format)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Формат схемы", "format", format));
        if (slot.state() != CampusMapFormatState.READY
                || !Objects.equals(slot.assetId(), assetKey)) {
            throw new ConflictException("assetId", assetId, "Ассет схемы ещё не готов");
        }
        CampusMapAdminRepository.AssetRow asset =
                repository
                        .findAsset(assetKey, plan.id(), format)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ассет схемы", "id", assetKey));
        if (asset.content() == null
                || asset.content().length == 0
                || asset.content().length > MAX_BYTES) {
            throw new IllegalStateException("campus map asset is invalid");
        }
        return new Download(asset.contentType(), asset.content(), format);
    }

    private Map<Long, PlanResponse> currentPlans(List<CampusMapAdminRepository.FloorRow> floors) {
        Map<Long, PlanResponse> plans = new java.util.HashMap<>();
        for (CampusMapAdminRepository.FloorRow floor : floors) {
            repository
                    .findCurrentPlan(floor.id())
                    .ifPresent(
                            plan -> {
                                if (plan.publishedAt() != null) {
                                    long revision =
                                            plan.catalogRevisionId() == null
                                                    ? 0
                                                    : catalogRevision(plan.catalogRevisionId());
                                    plans.put(
                                            floor.id(),
                                            toPlan(
                                                    plan,
                                                    revision,
                                                    repository.findSlots(plan.id()),
                                                    floor.buildingId()));
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

    private static FloorResponse toFloor(
            CampusMapAdminRepository.FloorRow floor, PlanResponse plan, long openCount) {
        return new FloorResponse(
                decimal(floor.id()),
                decimal(floor.buildingId()),
                floor.code(),
                floor.label(),
                plan,
                openCount,
                "all_time");
    }

    private static PlanResponse toPlan(
            CampusMapAdminRepository.PlanRow plan,
            long revision,
            List<CampusMapAdminRepository.SlotRow> rows,
            long buildingId) {
        Map<CampusMapFormat, CampusMapAdminRepository.SlotRow> slots =
                new EnumMap<>(CampusMapFormat.class);
        for (CampusMapAdminRepository.SlotRow row : rows) {
            slots.put(row.format(), row);
        }
        return new PlanResponse(
                decimal(buildingId),
                decimal(plan.floorId()),
                decimal(plan.version()),
                plan.label(),
                decimal(revision),
                slot(slots.get(CampusMapFormat.PNG)),
                slot(slots.get(CampusMapFormat.SVG)));
    }

    private static CampusMapAdminModels.FormatSlot slot(CampusMapAdminRepository.SlotRow row) {
        if (row == null) {
            throw new IllegalStateException("campus map plan format is missing");
        }
        return new CampusMapAdminModels.FormatSlot(
                row.format() == CampusMapFormat.PNG
                        ? CampusMapAdminModels.Format.png
                        : CampusMapAdminModels.Format.svg,
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
                    .filter(
                            slot ->
                                    slot.format() == format
                                            && slot.state() == CampusMapFormatState.READY
                                            && slot.assetId() != null)
                    .findFirst()
                    .flatMap(
                            slot ->
                                    repository.findAsset(
                                            slot.assetId(), current.get().id(), format))
                    .map(
                            asset ->
                                    new ValidatedAsset(
                                            format,
                                            asset.contentType(),
                                            asset.content(),
                                            asset.bytes(),
                                            asset.sha256(),
                                            asset.width(),
                                            asset.height()))
                    .ifPresent(asset -> assets.put(format, asset));
        }
        return assets;
    }

    private ValidatedAsset validate(MultipartFile file, CampusMapFormat format) {
        String expected = expectedMime(format);
        if (!expected.equals(file.getContentType())) {
            throw new BadRequestException(
                    "file",
                    "Файл "
                            + format.name().toLowerCase(Locale.ROOT)
                            + " должен иметь MIME "
                            + expected);
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
        Dimensions dimensions =
                format == CampusMapFormat.PNG ? validatePng(content) : validateSvg(content);
        return new ValidatedAsset(
                format,
                expected,
                content,
                content.length,
                sha256(content),
                dimensions.width(),
                dimensions.height());
    }

    private static Dimensions validatePng(byte[] content) {
        if (content.length < 8
                || !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(content, PNG_SIGNATURE.length))) {
            throw new BadRequestException("file", "PNG-файл имеет неверную сигнатуру");
        }
        int width = 0;
        int height = 0;
        int depth = 0;
        int color = 0;
        int interlace = 0;
        int firstData = -1;
        boolean palette = false;
        boolean dataEnded = false;
        boolean ended = false;
        for (int offset = 8; offset < content.length; ) {
            if (content.length - offset < 12) throw invalidPng();
            int length = pngInt(content, offset);
            if (length < 0 || length > content.length - offset - 12) throw invalidPng();
            String type =
                    new String(content, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (!type.matches("[A-Za-z]{2}[A-Z][A-Za-z]")) throw invalidPng();
            CRC32 crc = new CRC32();
            crc.update(content, offset + 4, length + 4);
            if (crc.getValue() != Integer.toUnsignedLong(pngInt(content, offset + 8 + length)))
                throw invalidPng();
            int data = offset + 8;
            if (offset == 8 && !"IHDR".equals(type)) throw invalidPng();
            switch (type) {
                case "IHDR" -> {
                    if (offset != 8 || length != 13) throw invalidPng();
                    width = pngInt(content, data);
                    height = pngInt(content, data + 4);
                    depth = Byte.toUnsignedInt(content[data + 8]);
                    color = Byte.toUnsignedInt(content[data + 9]);
                    interlace = Byte.toUnsignedInt(content[data + 12]);
                    boolean validDepth =
                            switch (color) {
                                case 0 ->
                                        depth == 1
                                                || depth == 2
                                                || depth == 4
                                                || depth == 8
                                                || depth == 16;
                                case 2, 4, 6 -> depth == 8 || depth == 16;
                                case 3 -> depth == 1 || depth == 2 || depth == 4 || depth == 8;
                                default -> false;
                            };
                    if (width <= 0
                            || height <= 0
                            || !validDepth
                            || content[data + 10] != 0
                            || content[data + 11] != 0
                            || interlace > 1) throw invalidPng();
                }
                case "PLTE" -> {
                    if (palette
                            || firstData >= 0
                            || color == 0
                            || color == 4
                            || length == 0
                            || length % 3 != 0
                            || length > 768
                            || color == 3 && length / 3 > (1 << depth)) throw invalidPng();
                    palette = true;
                }
                case "IDAT" -> {
                    if (dataEnded || color == 3 && !palette) throw invalidPng();
                    if (firstData < 0) firstData = offset;
                }
                case "IEND" -> {
                    if (length != 0 || firstData < 0 || offset + 12 != content.length)
                        throw invalidPng();
                    ended = true;
                }
                default -> {
                    // Unknown critical chunks cannot be safely interpreted as a PNG image.
                    if (Character.isUpperCase(type.charAt(0))) throw invalidPng();
                }
            }
            if (firstData >= 0 && !"IDAT".equals(type)) dataEnded = true;
            offset += length + 12;
        }
        if (!ended) throw invalidPng();
        validatePngScanlines(content, firstData, width, height, depth, color, interlace);
        return new Dimensions(width, height);
    }

    private static int pngInt(byte[] content, int offset) {
        return ByteBuffer.wrap(content, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static BadRequestException invalidPng() {
        return new BadRequestException(
                "file", "PNG-файл повреждён или содержит неполные данные изображения");
    }

    private static void validatePngScanlines(
            byte[] content,
            int offset,
            int width,
            int height,
            int depth,
            int color,
            int interlace) {
        int samples =
                switch (color) {
                    case 0, 3 -> 1;
                    case 2 -> 3;
                    case 4 -> 2;
                    case 6 -> 4;
                    default -> 0;
                };
        // PNG's seven Adam7 passes, or one whole-image pass. Sizes are arithmetic only:
        // no pixel/row array is allocated from untrusted dimensions.
        int[][] passes =
                interlace == 0
                        ? new int[][] {{0, 0, 1, 1}}
                        : new int[][] {
                            {0, 0, 8, 8},
                            {4, 0, 8, 8},
                            {0, 4, 4, 8},
                            {2, 0, 4, 4},
                            {0, 2, 2, 4},
                            {1, 0, 2, 2},
                            {0, 1, 1, 2}
                        };
        long remainingBytes = 134_217_728L;
        long remainingRows = 1_000_000L;
        for (int[] pass : passes) {
            long columns = Math.max(0L, ((long) width - pass[0] + pass[2] - 1) / pass[2]);
            long rows = Math.max(0L, ((long) height - pass[1] + pass[3] - 1) / pass[3]);
            if (columns == 0 || rows == 0) continue;
            long scanlineBytes = 1 + (columns * samples * depth + 7) / 8;
            // Compare before multiplication: even maximal legal IHDR dimensions cannot overflow.
            // Include each filter byte and every nonempty Adam7 pass, before starting inflation.
            if (rows > remainingRows || rows > remainingBytes / scanlineBytes) {
                throw new BadRequestException(
                        "file",
                        "PNG превышает бюджет проверки: 128 МиБ данных изображения и 1000000 строк проходов");
            }
            remainingRows -= rows;
            remainingBytes -= rows * scanlineBytes;
        }
        PngDataStream data = new PngDataStream(content, offset);
        Inflater inflater = new Inflater();
        try (InflaterInputStream pixels = new InflaterInputStream(data, inflater, 8192)) {
            for (int[] pass : passes) {
                long columns = Math.max(0L, ((long) width - pass[0] + pass[2] - 1) / pass[2]);
                long rows = Math.max(0L, ((long) height - pass[1] + pass[3] - 1) / pass[3]);
                if (columns == 0 || rows == 0) continue;
                long rowBytes = (columns * samples * depth + 7) / 8;
                for (long row = 0; row < rows; row++) {
                    if (Thread.currentThread().isInterrupted()) throw invalidPng();
                    int filter = pixels.read();
                    if (filter < 0 || filter > 4) throw invalidPng();
                    pixels.skipNBytes(rowBytes);
                }
            }
            if (pixels.read() != -1
                    || !inflater.finished()
                    || inflater.getRemaining() != 0
                    || data.read() != -1) {
                throw invalidPng();
            }
        } catch (IOException error) {
            throw invalidPng();
        } finally {
            inflater.end();
        }
    }

    /** Concatenates already checked consecutive IDAT chunks without copying their payloads. */
    private static final class PngDataStream extends InputStream {
        private final byte[] content;
        private int nextChunk;
        private int cursor;
        private int remaining;
        private boolean finished;

        private PngDataStream(byte[] content, int offset) {
            this.content = content;
            this.nextChunk = offset;
        }

        private boolean advance() {
            while (remaining == 0 && !finished) {
                if (nextChunk >= content.length
                        || content[nextChunk + 4] != 'I'
                        || content[nextChunk + 5] != 'D'
                        || content[nextChunk + 6] != 'A'
                        || content[nextChunk + 7] != 'T') {
                    finished = true;
                    return false;
                }
                remaining = pngInt(content, nextChunk);
                cursor = nextChunk + 8;
                nextChunk += remaining + 12;
            }
            return !finished;
        }

        @Override
        public int read() {
            if (!advance()) return -1;
            remaining--;
            return Byte.toUnsignedInt(content[cursor++]);
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, target.length);
            if (length == 0) return 0;
            if (!advance()) return -1;
            int count = Math.min(length, remaining);
            System.arraycopy(content, cursor, target, offset, count);
            cursor += count;
            remaining -= count;
            return count;
        }
    }

    private static Dimensions validateSvg(byte[] content) {
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        if (text.indexOf('\u0000') >= 0 || UNSAFE_TEXT.matcher(text).find()) {
            throw new BadRequestException(
                    "file", "SVG содержит запрещённый скрипт, внешний ресурс или шрифт");
        }
        try {
            var document =
                    secureXmlFactory()
                            .newDocumentBuilder()
                            .parse(new ByteArrayInputStream(content));
            Element root = document.getDocumentElement();
            if (root == null
                    || !"svg"
                            .equalsIgnoreCase(
                                    root.getLocalName() == null
                                            ? root.getNodeName()
                                            : root.getLocalName())) {
                throw new BadRequestException("file", "SVG должен иметь корневой элемент svg");
            }
            inspectSvg(document);
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

    private static DocumentBuilderFactory secureXmlFactory()
            throws javax.xml.parsers.ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static void inspectSvg(Node root) throws Exception {
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                    && "xml-stylesheet".equals(node.getNodeName())) {
                // A PI is outside the element tree, including before/after the SVG root.
                // Parse pseudoattributes with the same entity/DTD protections as the document.
                byte[] attributes =
                        ("<stylesheet " + node.getNodeValue() + "/>")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Element stylesheet =
                        secureXmlFactory()
                                .newDocumentBuilder()
                                .parse(new ByteArrayInputStream(attributes))
                                .getDocumentElement();
                String href = stylesheet.getAttribute("href").trim();
                if (!"stylesheet".equals(stylesheet.getNodeName())
                        || stylesheet.hasChildNodes()
                        || (!href.isEmpty() && !href.startsWith("#"))) {
                    throw new BadRequestException(
                            "file", "SVG содержит внешний ресурс таблицы стилей");
                }
            }
            if (node instanceof Element element) {
                String name =
                        (element.getLocalName() == null
                                        ? element.getNodeName()
                                        : element.getLocalName())
                                .toLowerCase(Locale.ROOT);
                if (SetOfUnsafeElements.contains(name)) {
                    throw new BadRequestException(
                            "file", "SVG содержит запрещённый элемент " + name);
                }
                if ("style".equals(name)) inspectCssResources(element.getTextContent());
                if ("set".equals(name) || name.startsWith("animate"))
                    inspectAnimatedResource(element);
                NamedNodeMap attributes = element.getAttributes();
                for (int index = 0; index < attributes.getLength(); index++) {
                    Node attribute = attributes.item(index);
                    String attrName = attribute.getNodeName().toLowerCase(Locale.ROOT);
                    String value =
                            attribute.getNodeValue() == null ? "" : attribute.getNodeValue().trim();
                    inspectCssResources(value);
                    if (attrName.startsWith("on")
                            || UNSAFE_TEXT.matcher(value).find()
                            || (resourceAttribute(attrName) && !safeSvgResource(value))) {
                        throw new BadRequestException(
                                "file", "SVG содержит внешний или исполняемый ресурс");
                    }
                }
            }
            NodeList children = node.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                queue.add(children.item(index));
            }
        }
    }

    private static boolean resourceAttribute(String name) {
        return "href".equals(name) || name.endsWith(":href") || "xml:base".equals(name);
    }

    private static boolean safeSvgResource(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.startsWith("#")) return true;
        for (String mime : List.of("data:image/png", "data:image/jpeg")) {
            if (value.startsWith(mime)
                    && value.length() > mime.length()
                    && (value.charAt(mime.length()) == ';' || value.charAt(mime.length()) == ','))
                return true;
        }
        return false;
    }

    private static void inspectAnimatedResource(Element element) {
        String target = element.getAttribute("attributeName").trim().toLowerCase(Locale.ROOT);
        if (target.startsWith("on")) {
            throw new BadRequestException("file", "SVG содержит анимацию исполняемого атрибута");
        }
        if (!resourceAttribute(target)) return;
        for (String attribute : List.of("to", "from", "by")) {
            if (!safeSvgResource(element.getAttribute(attribute))) {
                throw new BadRequestException("file", "SVG содержит анимацию внешнего ресурса");
            }
        }
        for (String value : element.getAttribute("values").split(";", -1)) {
            if (!safeSvgResource(value)) {
                throw new BadRequestException("file", "SVG содержит анимацию внешнего ресурса");
            }
        }
    }

    private static void inspectCssResources(String raw) {
        String decoded = unescapeCss(raw);
        if (UNSAFE_TEXT.matcher(decoded).find()) {
            throw new BadRequestException(
                    "file", "SVG содержит запрещённый скрипт, внешний ресурс или шрифт");
        }
        if (!CSS_RESOURCE.matcher(decoded).find()) return;
        // Scan original tokens: decoding an escaped quote or deleting a comment inside
        // a string before finding its boundary changes the resource's actual meaning.
        // The stack avoids recursive parsing of attacker-controlled parentheses.
        ArrayDeque<CssFunction> functions = new ArrayDeque<>();
        int resourceFunctions = 0;
        for (int index = 0; index < raw.length(); ) {
            index = skipCssSpace(raw, index);
            if (index == raw.length()) break;
            char current = raw.charAt(index);
            CssFunction enclosing = functions.peek();
            if (current == '\'' || current == '"') {
                int end;
                try {
                    end = cssStringEnd(raw, index);
                } catch (BadRequestException malformedString) {
                    // Non-CSS SVG attributes are inspected too; an ordinary apostrophe
                    // outside a function must not turn a harmless label into a rejection.
                    if (enclosing != null) throw malformedString;
                    index++;
                    continue;
                }
                if (enclosing != null
                        && enclosing.resourceStrings
                        && enclosing.sourceStart
                        && !safeSvgResource(unescapeCss(raw.substring(index + 1, end - 1)))) {
                    throw unsafeCssResource();
                }
                if (enclosing != null) enclosing.sourceStart = false;
                index = end;
            } else if (cssIdentifierChar(current)) {
                int end = index;
                while (end < raw.length() && cssIdentifierChar(raw.charAt(end))) {
                    end = raw.charAt(end) == '\\' ? cssEscapeEnd(raw, end) : end + 1;
                }
                String name = unescapeCss(raw.substring(index, end)).toLowerCase(Locale.ROOT);
                int open = skipCssSpace(raw, end);
                if (open < raw.length() && raw.charAt(open) == '(') {
                    if ("var".equals(name) && resourceFunctions > 0) {
                        // Resource strings hidden by custom-property substitution are unresolved.
                        throw unsafeCssResource();
                    }
                    if (enclosing != null) enclosing.sourceStart = false;
                    if ("url".equals(name) || "src".equals(name)) {
                        index = inspectCssUrl(raw, open + 1);
                    } else {
                        boolean resourceStrings =
                                "image-set".equals(name)
                                        || "-webkit-image-set".equals(name)
                                        || "image".equals(name);
                        functions.push(new CssFunction(resourceStrings));
                        if (resourceStrings) resourceFunctions++;
                        index = open + 1;
                    }
                } else {
                    // image() may put its optional ltr/rtl direction before a string source.
                    if (enclosing != null && !("ltr".equals(name) || "rtl".equals(name))) {
                        enclosing.sourceStart = false;
                    }
                    index = end;
                }
            } else {
                if (current == '(') functions.push(new CssFunction(false));
                else if (current == ')') {
                    if (!functions.isEmpty() && functions.pop().resourceStrings)
                        resourceFunctions--;
                } else if (current == ',' && enclosing != null) enclosing.sourceStart = true;
                else if (enclosing != null) enclosing.sourceStart = false;
                index++;
            }
        }
        if (!functions.isEmpty()) throw unsafeCssResource();
    }

    private static int inspectCssUrl(String css, int start) {
        start = skipCssSpace(css, start);
        if (start == css.length()) throw unsafeCssResource();
        String resource;
        int close;
        if (css.charAt(start) == '\'' || css.charAt(start) == '"') {
            int end = cssStringEnd(css, start);
            resource = unescapeCss(css.substring(start + 1, end - 1));
            close = skipCssSpace(css, end);
        } else {
            int end = start;
            while (end < css.length() && css.charAt(end) != ')') {
                if (css.charAt(end) == '(' || css.charAt(end) == '\'' || css.charAt(end) == '"') {
                    throw unsafeCssResource();
                }
                end = css.charAt(end) == '\\' ? cssEscapeEnd(css, end) : end + 1;
            }
            resource = unescapeCss(css.substring(start, end));
            close = end;
        }
        if (close == css.length() || css.charAt(close) != ')' || !safeSvgResource(resource)) {
            throw unsafeCssResource();
        }
        return close + 1;
    }

    private static int skipCssSpace(String css, int index) {
        while (index < css.length()) {
            if (Character.isWhitespace(css.charAt(index))) index++;
            else if (css.startsWith("/*", index)) {
                int end = css.indexOf("*/", index + 2);
                if (end < 0) throw unsafeCssResource();
                index = end + 2;
            } else break;
        }
        return index;
    }

    private static int cssStringEnd(String css, int start) {
        char quote = css.charAt(start);
        for (int index = start + 1; index < css.length(); ) {
            char current = css.charAt(index);
            if (current == quote) return index + 1;
            if (current == '\r' || current == '\n' || current == '\f') throw unsafeCssResource();
            index = current == '\\' ? cssEscapeEnd(css, index) : index + 1;
        }
        throw unsafeCssResource();
    }

    private static boolean cssIdentifierChar(char value) {
        return Character.isLetterOrDigit(value)
                || value == '-'
                || value == '_'
                || value == '\\'
                || value >= 128;
    }

    private static int cssEscapeEnd(String css, int start) {
        int index = start + 1;
        if (index == css.length()) throw unsafeCssResource();
        int digits = 0;
        while (index < css.length() && digits < 6 && Character.digit(css.charAt(index), 16) >= 0) {
            index++;
            digits++;
        }
        if (digits == 0) {
            if (css.charAt(index) == '\r'
                    && index + 1 < css.length()
                    && css.charAt(index + 1) == '\n') index++;
            return index + 1;
        }
        if (index < css.length() && Character.isWhitespace(css.charAt(index))) {
            if (css.charAt(index) == '\r'
                    && index + 1 < css.length()
                    && css.charAt(index + 1) == '\n') index++;
            index++;
        }
        return index;
    }

    private static final class CssFunction {
        private final boolean resourceStrings;
        private boolean sourceStart = true;

        private CssFunction(boolean resourceStrings) {
            this.resourceStrings = resourceStrings;
        }
    }

    private static BadRequestException unsafeCssResource() {
        return new BadRequestException("file", "SVG содержит внешний CSS-ресурс");
    }

    private static String unescapeCss(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 == value.length()) {
                result.append(current);
                continue;
            }
            int next = index + 1;
            int codepoint = 0;
            int digits = 0;
            while (next < value.length() && digits < 6) {
                int digit = Character.digit(value.charAt(next), 16);
                if (digit < 0) break;
                codepoint = codepoint * 16 + digit;
                next++;
                digits++;
            }
            if (digits > 0) {
                result.appendCodePoint(
                        codepoint == 0
                                        || !Character.isValidCodePoint(codepoint)
                                        || codepoint >= 0xd800 && codepoint <= 0xdfff
                                ? 0xfffd
                                : codepoint);
                if (next < value.length() && Character.isWhitespace(value.charAt(next))) {
                    if (value.charAt(next) == '\r'
                            && next + 1 < value.length()
                            && value.charAt(next + 1) == '\n') next++;
                    next++;
                }
                index = next - 1;
            } else {
                char escaped = value.charAt(next);
                if (escaped != '\r' && escaped != '\n' && escaped != '\f') result.append(escaped);
                if (escaped == '\r' && next + 1 < value.length() && value.charAt(next + 1) == '\n')
                    next++;
                index = next;
            }
        }
        return result.toString();
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

    static String requireUploadCode(String value, String field) {
        if (value == null || value.length() > 64 || !POSITIVE_DECIMAL.matcher(value).matches()) {
            throw new BadRequestException(
                    field,
                    "Код должен содержать положительное число без пробелов, максимум 64 цифры");
        }
        return value;
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

    public record Download(String contentType, byte[] content, CampusMapFormat format) {}

    private record ValidatedAsset(
            CampusMapFormat format,
            String contentType,
            byte[] content,
            long bytes,
            byte[] sha256,
            Integer width,
            Integer height) {}

    private record Dimensions(Integer width, Integer height) {}

    private static final class Hex {
        private Hex() {}

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
        private static final java.util.Set<String> values =
                java.util.Set.of(
                        "script",
                        "foreignobject",
                        "iframe",
                        "object",
                        "embed",
                        "audio",
                        "video",
                        "link");

        private static boolean contains(String value) {
            return values.contains(value);
        }

        private SetOfUnsafeElements() {}
    }
}
