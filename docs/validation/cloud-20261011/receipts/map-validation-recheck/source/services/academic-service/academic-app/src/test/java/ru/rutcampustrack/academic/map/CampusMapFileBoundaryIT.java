package ru.rutcampustrack.academic.map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.security.RoleCheckAspect;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PG BYTEA and MVC/AOP proof; no browser or signed JWT admission is inferred. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampusMapFileBoundaryIT {
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withLabel("rct.package", "file-boundaries-20261011");
    private JdbcTemplate jdbc;
    private CampusMapAdminService admin;
    private RequestContext context;
    private MockMvc mvc;
    private UUID session;

    @BeforeAll
    void start() {
        postgres.start();
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @BeforeEach
    void schemaAndLiveAuthority() {
        String schema = "files_" + UUID.randomUUID().toString().replace("-", "");
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
        source.setSchema(schema);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).cleanDisabled(true).target("54").load().migrate();
        jdbc = new JdbcTemplate(source);
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(new DataSourceTransactionManager(source));
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var serviceProxy = new ProxyFactory(new CampusMapAdminService(new CampusMapAdminRepository(jdbc)));
        serviceProxy.setProxyTargetClass(true);
        serviceProxy.addAdvice(transaction);
        admin = (CampusMapAdminService) serviceProxy.getProxy();

        jdbc.update("INSERT INTO users(id,login,last_name,first_name) VALUES (700,'files-admin','Synthetic','Admin')");
        jdbc.update("INSERT INTO admin_access_profiles(user_id,assigned,mode,version) VALUES (700,TRUE,'FULL',1)");
        Long grant = jdbc.queryForObject("""
                INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at)
                VALUES (700,'admin','active',NOW(),NOW()) RETURNING id
                """, Long.class);
        session = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,
                    refresh_expires_at,created_at,last_seen_at,auth_method)
                VALUES (?,700,?,1,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')
                """, session, grant, UUID.randomUUID());
        context = new RequestContext();
        context.setUserId(700L);
        context.setRole(UserRole.ADMIN);
        refreshClaims();
        var authority = new AcademicAdminAccess(new AdminAccessJdbcAuthority(jdbc), context, jdbc);
        var aspect = new RoleCheckAspect(context);
        ReflectionTestUtils.setField(aspect, "adminAccess", authority);
        // Deletion is outside this new payload boundary; no deletion request can reach this null dependency.
        var controller = new AspectJProxyFactory(new CampusMapAdminController(admin, null));
        controller.setProxyTargetClass(true);
        controller.addAspect(aspect);
        Object securedController = controller.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(securedController)
                .setControllerAdvice(new ru.rutcampustrack.academic.exception.GlobalExceptionHandler(),
                        new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler()).build();
    }

    @Test
    void decodedPngAndSafeSvgRoundTripThroughRealStorageAndHttpWithHistory() throws Exception {
        byte[] png = png();
        mvc.perform(upload().file(file("png", "схема.png", "image/png", png)))
                .andExpect(status().isCreated());
        PlanResponse first = admin.listBuildings().getFirst().floors().getFirst().currentPlan();
        byte[] downloaded = mvc.perform(get(assetUrl(first, "png", first.png().id())))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Length", Integer.toString(png.length)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"floor-"
                        + first.floorId() + "-v1-png\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(downloaded));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(2);
        assertThat(image.getHeight()).isEqualTo(2);
        assertThat(image.getRGB(0, 0)).isEqualTo(0xffff0000);
        assertThat(image.getRGB(1, 1)).isEqualTo(0xff0000ff);

        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"2\" height=\"2\">"
                + "<defs><linearGradient id=\"local\"/></defs><rect width=\"2\" height=\"2\" "
                + "fill=\"url(#local)\"/><text>Синтетическая схема</text></svg>").getBytes(StandardCharsets.UTF_8);
        mvc.perform(upload().file(file("svg", "схема.svg", "image/svg+xml", svg)))
                .andExpect(status().isOk());
        PlanResponse second = admin.listBuildings().getFirst().floors().getFirst().currentPlan();
        assertThat(second.version()).isEqualTo("2");
        assertThat(second.png().sha256()).isEqualTo(first.png().sha256());
        mvc.perform(get(assetUrl(second, "svg", second.svg().id())))
                .andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().bytes(svg));
        mvc.perform(get(assetUrl(first, "png", first.png().id())))
                .andExpect(status().isOk()).andExpect(content().bytes(png));
    }

    @ParameterizedTest
    @ValueSource(strings = {"header-only", "truncated", "checksum", "compressed-data", "huge-incomplete-raster"})
    void undecodablePngCannotPublishAnyInventoryOrFile(String corruption) throws Exception {
        byte[] bytes;
        if ("header-only".equals(corruption)) {
            bytes = new byte[24];
            System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, 0, bytes, 0, 8);
            ByteBuffer.wrap(bytes).putInt(16, 2).putInt(20, 2);
        } else if ("truncated".equals(corruption)) {
            bytes = Arrays.copyOf(png(), 33); // IHDR survives; all actual pixel data is absent.
        } else if ("checksum".equals(corruption)) {
            bytes = png();
            bytes[32] ^= 1;
        } else if ("compressed-data".equals(corruption)) {
            bytes = png();
            int idat = chunk(bytes, "IDAT");
            bytes[idat + 8] = 0; // Bad zlib header with a valid PNG chunk checksum.
            repairCrc(bytes, idat);
        } else {
            bytes = png();
            ByteBuffer.wrap(bytes).putInt(16, Integer.MAX_VALUE).putInt(20, Integer.MAX_VALUE);
            repairCrc(bytes, 8); // Valid header/CRC cannot justify allocating an enormous pixel array.
        }
        assertRejectedWithoutWrites(file("png", "сломанная.png", "image/png", bytes));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<image href='https://example.invalid/rct-file-marker'/>",
            "<rect onload='alert(1)'/>",
            "<image href='#local'><set attributeName='href' to='https://example.invalid/rct-file-marker' begin='0s'/></image>",
            "<image href='#local'><animate attributeName='href' values='#local;https://example.invalid/rct-file-marker' dur='1s'/></image>",
            "<style>rect { fill: url('/rct-file-marker.svg#paint'); }</style><rect width='2' height='2'/>",
            "<style>rect { fill: u\\72l('\\2f rct-file-marker.svg#paint'); }</style>",
            "<style>@\\69mport '//example.invalid/rct-file-marker.css';</style>",
            "<use xml:base='https://example.invalid/rct-file-marker.svg' href='#paint'/>"
    })
    void svgCannotPublishScriptOrForeignResourceReferences(String child) throws Exception {
        byte[] svg = ("<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2'>"
                + child + "</svg>").getBytes(StandardCharsets.UTF_8);
        assertRejectedWithoutWrites(file("svg", "небезопасная.svg", "image/svg+xml", svg));
    }

    @Test
    void svgDoctypeCannotResolveAnExternalEntityOrPublishAFile() throws Exception {
        byte[] svg = ("<!DOCTYPE svg [<!ENTITY external SYSTEM 'file:///rct-synthetic-nonexistent-marker'>]>"
                + "<svg xmlns='http://www.w3.org/2000/svg'><text>&external;</text></svg>")
                .getBytes(StandardCharsets.UTF_8);
        assertRejectedWithoutWrites(file("svg", "xxe.svg", "image/svg+xml", svg));
    }

    @ParameterizedTest
    @ValueSource(strings = {"before-root", "after-root", "relative", "entity-reference"})
    void documentLevelStylesheetCannotPublishExternalResources(String position) throws Exception {
        String target = switch (position) {
            case "relative" -> "/rct-file-marker.css";
            case "entity-reference" -> "&#104;ttps://example.invalid/rct-file-marker.css";
            default -> "https://example.invalid/rct-file-marker.css";
        };
        String instruction = "<?xml-stylesheet type='text/css' href='" + target + "'?>";
        String root = "<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2'><rect width='2' height='2'/></svg>";
        String document = "<?xml version='1.0' encoding='UTF-8'?>"
                + ("after-root".equals(position) ? root + instruction : instruction + root);
        assertRejectedWithoutWrites(file("svg", "stylesheet.svg", "image/svg+xml", document.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "background-image:image-set('https://example.invalid/rct-file-marker.png' 1x)",
            "background-image:-webkit-image-set('//example.invalid/rct-file-marker.png' 1x)",
            "background-image:image-set('/rct-file-marker.png' 1x)",
            "background-image:i\\6d age-set('\\2f rct-file-marker.png' 1x)",
            "background-image:image-set(url('#local') 1x, 'https://example.invalid/rct-file-marker.png' 2x)",
            "background-image:image-set('/*rct-marker*/#paint' 1x)",
            "background-image:image-set('\\27 /rct-file-marker.png' 1x)"
    })
    void cssImageSetCannotPublishQuotedExternalResource(String css) throws Exception {
        String attribute = css.replace("'", "&apos;");
        byte[] svg = ("<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2' style='"
                + attribute + "'><style>rect { " + css + " }</style><rect width='2' height='2'/></svg>")
                .getBytes(StandardCharsets.UTF_8);
        assertRejectedWithoutWrites(file("svg", "image-set.svg", "image/svg+xml", svg));
    }

    @Test
    void xmlDeclarationLocalStylesheetAndImageSetFragmentRemainAccepted() throws Exception {
        byte[] svg = ("<?xml version='1.0' encoding='UTF-8'?>"
                + "<?xml-stylesheet type='text/css' href='#local-css'?>"
                + "<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2'>"
                + "<defs><linearGradient id='paint'/></defs>"
                + "<style id='local-css'>rect { fill: url('#paint'); background-image:image-set('#paint' 1x type('image/png')); "
                + "border-image:image-set('#paint\\27 local' 1x); }</style>"
                + "<rect width='2' height='2'/></svg>").getBytes(StandardCharsets.UTF_8);
        mvc.perform(upload().file(file("svg", "local-styles.svg", "image/svg+xml", svg)))
                .andExpect(status().isCreated());
        var plan = admin.listBuildings().getFirst().floors().getFirst().currentPlan();
        mvc.perform(get(assetUrl(plan, "svg", plan.svg().id())))
                .andExpect(status().isOk()).andExpect(content().bytes(svg));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rows", "decoded-bytes", "adam7-pass-rows", "overflow-header"})
    void pngOverAcceptedProcessingBudgetCannotPublishAnyInventoryOrFile(String boundary) throws Exception {
        byte[] png = switch (boundary) {
            case "rows" -> CampusMapFileFixture.streamingGrayPng(1, 1_000_001, 1, false);
            case "decoded-bytes" -> CampusMapFileFixture.streamingGrayPng(134_217_728, 1, 8, false);
            case "overflow-header" -> {
                byte[] header = png();
                ByteBuffer.wrap(header).putInt(16, Integer.MAX_VALUE).putInt(20, Integer.MAX_VALUE);
                header[24] = 16; // Full canvas byte product exceeds Long.MAX_VALUE; do not inflate it.
                repairCrc(header, 8);
                yield header;
            }
            // Adam7 validates 1,000,001 pass rows here, though the canvas has only 666,667 rows.
            default -> CampusMapFileFixture.streamingGrayPng(2, 666_667, 1, true);
        };
        var error = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                assertRejectedWithoutWrites(file("png", "processing-budget.png", "image/png", png)));
        assertThat(error.path("field").asText()).isEqualTo("file");
        assertThat(error.path("detail").asText()).contains("128", "1000000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rows", "decoded-bytes"})
    void pngExactlyAtAcceptedProcessingBudgetRemainsStoredAndServed(String boundary) throws Exception {
        int width = "rows".equals(boundary) ? 1 : 134_217_727;
        int height = "rows".equals(boundary) ? 1_000_000 : 1;
        int depth = "rows".equals(boundary) ? 1 : 8;
        byte[] png = CampusMapFileFixture.streamingGrayPng(width, height, depth, false);
        mvc.perform(upload().file(file("png", "at-processing-budget.png", "image/png", png)))
                .andExpect(status().isCreated());
        var plan = admin.listBuildings().getFirst().floors().getFirst().currentPlan();
        assertThat(plan.png().width()).isEqualTo(width);
        assertThat(plan.png().height()).isEqualTo(height);
        mvc.perform(get(assetUrl(plan, "png", plan.png().id())))
                .andExpect(status().isOk()).andExpect(content().bytes(png));
    }

    @Test
    void acceptedPngColorsInterlaceAndLargeDimensionsDoNotRequireRasterAllocationInValidator() throws Exception {
        for (boolean interlaced : List.of(false, true)) {
            byte[] gray = CampusMapFileFixture.streamingGrayPng(7, 5, 1, interlaced);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(gray));
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(7);
            assertThat(decoded.getHeight()).isEqualTo(5);
            assertThat(decoded.getRGB(0, 0)).isEqualTo(0xff000000);
            var plan = admin.uploadPlan("71", "1", null, file("png", "stream-gray.png", "image/png", gray), null).plan();
            mvc.perform(get(assetUrl(plan, "png", plan.png().id())))
                    .andExpect(status().isOk()).andExpect(content().bytes(gray));
        }
        for (int type : List.of(BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_USHORT_GRAY,
                BufferedImage.TYPE_BYTE_INDEXED, BufferedImage.TYPE_INT_ARGB)) {
            byte[] encoded = CampusMapFileFixture.png(7, 5, type, true);
            var plan = admin.uploadPlan("71", "1", null, file("png", "color.png", "image/png", encoded), null).plan();
            assertThat(plan.png().width()).isEqualTo(7);
            assertThat(plan.png().height()).isEqualTo(5);
            mvc.perform(get(assetUrl(plan, "png", plan.png().id())))
                    .andExpect(status().isOk()).andExpect(content().bytes(encoded));
        }
        byte[] wide = CampusMapFileFixture.png(100_000, 1, BufferedImage.TYPE_BYTE_GRAY, false);
        var plan = admin.uploadPlan("71", "1", null, file("png", "wide.png", "image/png", wide), null).plan();
        assertThat(plan.png().width()).isEqualTo(100_000);
        mvc.perform(get(assetUrl(plan, "png", plan.png().id())))
                .andExpect(status().isOk()).andExpect(content().bytes(wide));
    }

    @Test
    void safeStyleAnimationAndEmbeddedRasterRemainAccepted() throws Exception {
        String raster = java.util.Base64.getEncoder().encodeToString(png());
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", jpegBytes)).isTrue();
        assertThat(ImageIO.read(new ByteArrayInputStream(jpegBytes.toByteArray()))).isNotNull();
        String jpeg = java.util.Base64.getEncoder().encodeToString(jpegBytes.toByteArray());
        byte[] svg = ("<svg xmlns='http://www.w3.org/2000/svg' width='2' height='2'>"
                + "<defs><linearGradient id='paint'/></defs><style>rect { fill: u\\72l('#paint'); }</style>"
                + "<rect width='2' height='2'><animate attributeName='opacity' values='0;1' dur='1s'/></rect>"
                + "<use href='#paint'><set attributeName='href' to='#paint' begin='0s'/></use>"
                + "<image href='data:image/png;base64," + raster + "'/>"
                + "<image href='data:image/jpeg;base64," + jpeg + "'/></svg>").getBytes(StandardCharsets.UTF_8);
        mvc.perform(upload().file(file("svg", "safe.svg", "image/svg+xml", svg))).andExpect(status().isCreated());
        var plan = admin.listBuildings().getFirst().floors().getFirst().currentPlan();
        mvc.perform(get(assetUrl(plan, "svg", plan.svg().id())))
                .andExpect(status().isOk()).andExpect(content().bytes(svg));
    }

    @Test
    void foreignAssetAndRevokedLiveMapGrantNeverReturnFileBytes() throws Exception {
        PlanResponse first = admin.uploadPlan("71", "1", null, file("png", "one.png", "image/png", png()), null).plan();
        PlanResponse other = admin.uploadPlan("71", "2", null, file("png", "two.png", "image/png", png()), null).plan();
        mvc.perform(get(assetUrl(first, "png", other.png().id())))
                .andExpect(status().isConflict());
        mvc.perform(get(assetUrl(first, "png", first.png().id())))
                .andExpect(status().isOk()).andExpect(content().bytes(png()));
        jdbc.update("UPDATE admin_access_profiles SET mode='LIMITED',permissions=ARRAY['USERS_VIEW'],version=version+1 WHERE user_id=700");
        refreshClaims(); // Fresh selected session remains live, but it no longer has CAMPUS_MAP_MANAGE.
        byte[] denied = mvc.perform(get(assetUrl(first, "png", first.png().id())))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsByteArray();
        assertThat(denied).isNotEqualTo(png());
        mvc.perform(upload().file(file("png", "three.png", "image/png", png())))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_map_plan_version", Long.class)).isEqualTo(2);
    }

    private String assertRejectedWithoutWrites(MockMultipartFile payload) throws Exception {
        long revisionCount = jdbc.queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class);
        var response = mvc.perform(upload().file(payload)).andReturn().getResponse();
        if (response.getStatus() == 201) {
            // A failing rejection must establish the stored/served result as well as the upload status.
            PlanResponse admitted = admin.listBuildings().getFirst().floors().getFirst().currentPlan();
            String format = payload.getName();
            String id = "png".equals(format) ? admitted.png().id() : admitted.svg().id();
            byte[] served = mvc.perform(get(assetUrl(admitted, format, id)))
                    .andExpect(status().isOk()).andExpect(content().bytes(payload.getBytes()))
                    .andReturn().getResponse().getContentAsByteArray();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(served));
            long assets = jdbc.queryForObject("SELECT count(*) FROM campus_map_asset", Long.class);
            System.out.println("Unexpected admitted file: status=201 servedStatus=200 byteEquality=true"
                    + " format=" + format + " bytes=" + served.length + " sha256=" + hash + " storedAssets=" + assets);
            String evidenceDirectory = System.getenv("RCT_FILES_EVIDENCE_DIR");
            if (evidenceDirectory != null && !evidenceDirectory.isBlank()) {
                Path directory = Path.of(evidenceDirectory);
                Files.createDirectories(directory);
                Files.write(directory.resolve(hash + "." + format), served);
            }
        }
        assertThat(response.getStatus()).as("unsafe or invalid file upload must be rejected").isEqualTo(400);
        for (String table : List.of("campus_map_building", "campus_map_floor", "campus_map_plan_version",
                "campus_map_asset", "campus_map_plan_format")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class)).isEqualTo(revisionCount);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private void refreshClaims() {
        context.setClaims(new InternalJwtClaims(700, session, 1,
                jdbc.queryForObject("SELECT roles_version FROM users WHERE id=700", Long.class),
                "ADMIN", "ACTIVE", null, false, false));
    }

    private static MockMultipartHttpServletRequestBuilder upload() {
        return multipart("/academic/map/plans")
                .file(file("buildingCode", "", "text/plain", "71".getBytes(StandardCharsets.UTF_8)))
                .file(file("floorCode", "", "text/plain", "1".getBytes(StandardCharsets.UTF_8)));
    }

    private static MockMultipartFile file(String part, String name, String mime, byte[] bytes) {
        return new MockMultipartFile(part, name, mime, bytes);
    }

    private static String assetUrl(PlanResponse plan, String format, String id) {
        return "/academic/map/floors/" + plan.floorId() + "/versions/" + plan.version()
                + "/assets/" + format + "/" + id;
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        image.setRGB(1, 1, 0xff0000ff);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }

    private static int chunk(byte[] png, String type) {
        for (int offset = 8; offset < png.length;) {
            int length = ByteBuffer.wrap(png).getInt(offset);
            if (new String(png, offset + 4, 4, StandardCharsets.US_ASCII).equals(type)) return offset;
            offset += length + 12;
        }
        throw new AssertionError("Synthetic PNG has no " + type);
    }

    private static void repairCrc(byte[] png, int offset) {
        int length = ByteBuffer.wrap(png).getInt(offset);
        CRC32 crc = new CRC32();
        crc.update(png, offset + 4, length + 4);
        ByteBuffer.wrap(png).putInt(offset + 8 + length, (int) crc.getValue());
    }
}
