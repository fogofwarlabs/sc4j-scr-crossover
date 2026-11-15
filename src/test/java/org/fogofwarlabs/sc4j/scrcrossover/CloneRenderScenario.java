package org.fogofwarlabs.sc4j.scrcrossover;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.engine.client.host.ClientHost;
import org.fogofwarlabs.sc4j.engine.client.host.HostWindow;
import org.fogofwarlabs.sc4j.engine.client.host.WindowSettings;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.WorldSceneExtraction;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptHeader;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptInstruction;
import org.fogofwarlabs.sc4j.scr.data.format.iscript.IscriptOpcodes;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.render.ClassicSceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.fogofwarlabs.sc4j.scr.render.SceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.WorldRenderer;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws sample sheets of an added unit of SC:R Crossover inside the clone ({@link CloneRunScenario}) in a hidden OpenGL context: every animation
 * (rows idle, walk, attack at its action frame, death) in five facings (south, south-east, east, north and the mirrored west), each with its shadow,
 * beside native units (Unity-chan: a Terran Marine and Terran Goliath; the Myriarch: a Zerg Hive), in the SD renderer, the HD renderer and the HD
 * renderer with lighting, for two player colors. It checks that the unit draws, that its team color follows the player color in each mode and
 * that lighting changes the HD picture, and writes the sheets as PNG files.
 */
public final class CloneRenderScenario {
    /**
     * The side of a cell of Unity-chan's and of the Myriarch's sheet in SD pixels: the unit's frames fit. The sheets are drawn one cell at a time
     * into a hidden window of one HD cell ({@link #HD_PIXEL_SCALE} times larger), which fits every screen.
     */
    private static final int UNITY_CHAN_CELL = 96;
    private static final int MYRIARCH_CELL = 224;
    private static final int HD_PIXEL_SCALE = 4;
    private static final int JUNGLE_TILESET = 4;
    private static final int TILE = 0x20;
    private static final int MAP_TILES = 128;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final int DIRECTIONS = 17;
    private static final int MIRRORED_WEST = 8;
    /**
     * The facings of the columns after the Marine and the Goliath: south, south-east, east, north, and west (east mirrored).
     */
    private static final int @NotNull [] FACINGS = {16, 12, 8, 0, MIRRORED_WEST};
    /**
     * The frame set of each row: the middle idle frame, a walk frame, the attack's action frame (Unity-chan's muzzle flash, the Myriarch's fire
     * breath) and the last death frame; Unity-chan's and the Myriarch's.
     */
    private static final int @NotNull [] UNITY_CHAN_ROW_FRAME_SETS = {2, 7, 15, 24};
    private static final int @NotNull [] MYRIARCH_ROW_FRAME_SETS = {4, 12, 28, 43};
    /**
     * The row of the Myriarch's sheet that draws its fire breath, and the breath's frame set drawn there (its first full flame).
     */
    private static final int MYRIARCH_FIRE_ROW = 2;
    private static final int FIRE_FRAME_SET = 2;
    /**
     * The images before the Myriarch's shadow: Unity-chan's two and the Myriarch's fire breath; the shadow and the Myriarch's image follow.
     */
    private static final int MYRIARCH_IMAGES = 3;
    private static final int FIRE_IMAGE = 2;
    /**
     * The SD pixels a shadow is drawn below its unit: none for Unity-chan, a native air unit's 42 for the flying Myriarch.
     */
    private static final int UNITY_CHAN_SHADOW_OFFSET = 0;
    private static final int MYRIARCH_SHADOW_OFFSET = 42;
    /**
     * The attack overlay table and its position where the Myriarch's breath starts.
     */
    private static final int ATTACK_OVERLAY = 0;
    private static final int BREATH_POSITION = 0;
    private static final int NO_OVERLAY = -1;
    /**
     * The image of a native unit drawn without a shadow image.
     */
    private static final int NO_SHADOW = -1;
    /**
     * The frame sets of the direction sheet's Myriarch rows: its first idle and first flight frame.
     */
    private static final int @NotNull [] DIRECTION_SHEET_FRAME_SETS = {0, 8};
    /**
     * The elements of a direction sheet row: its shadow image, main image, frame set and shadow offset.
     */
    private static final int ROW_SHADOW_IMAGE = 0;
    private static final int ROW_MAIN_IMAGE = 1;
    private static final int ROW_FRAME_SET = 2;
    private static final int ROW_SHADOW_OFFSET = 3;
    /**
     * The texture pixels per game pixel of the direction sheet's HD drawing (the HD2 quality, so the sheet stays a few thousand pixels wide).
     */
    private static final int HD2_PIXEL_SCALE = 2;
    /**
     * The walk sheet: its cells (the walk's, then the death's), the frames before each cell of the walk and of the death, and the pixels the
     * Myriarch is sent from the Hive's place.
     */
    private static final int WALK_CELLS = 8;
    private static final int DEATH_CELLS = 2;
    private static final int WALK_SHOT_FRAMES = 12;
    private static final int DEATH_SHOT_FRAMES = 7;
    private static final int WALK_DISTANCE = 256;
    private static final int RED_PLAYER = 0;
    private static final int BLUE_PLAYER = 1;
    /**
     * The animation of an image's script that creates its shadow image.
     */
    private static final int INIT_ANIMATION = 0;

    /**
     * The images and layout of a sample sheet.
     */
    private static final class Sheet {
        private final @NotNull String name;
        private final int cell;
        private final int @NotNull [] rowFrameSets;
        private final int mainImage;
        private final int shadowImage;
        private final int shadowOffset;
        /**
         * The overlay image drawn on the row {@link #MYRIARCH_FIRE_ROW} ({@link #NO_OVERLAY} for none) and its offset for each facing column.
         */
        private final int overlayImage;
        private final int @NotNull [] @NotNull [] overlayOffsets;
        /**
         * The frame the native units are drawn with: facing south ({@code 16}) for a unit, {@code 0} for a building.
         */
        private final int nativeFrame;
        /**
         * The images of each native unit, its shadow image first ({@link #NO_SHADOW} for none); one column each, before the unit's columns.
         */
        private final int @NotNull [] @NotNull [] nativeImages;

        private Sheet(@NotNull String name, int cell, int @NotNull [] rowFrameSets, int mainImage, int shadowImage, int shadowOffset,
                      int overlayImage, int @NotNull [] @NotNull [] overlayOffsets, int nativeFrame, int @NotNull [] @NotNull ... nativeImages) {
            this.name           = name;
            this.cell           = cell;
            this.rowFrameSets   = rowFrameSets;
            this.mainImage      = mainImage;
            this.shadowImage    = shadowImage;
            this.shadowOffset   = shadowOffset;
            this.overlayImage   = overlayImage;
            this.overlayOffsets = overlayOffsets;
            this.nativeFrame    = nativeFrame;
            this.nativeImages   = nativeImages;
        }

        private int getColumns() {
            return this.nativeImages.length + FACINGS.length;
        }

        private int getWidth() {
            return this.getColumns() * this.cell;
        }

        private int getHeight() {
            return this.rowFrameSets.length * this.cell;
        }

        /**
         * Draws the sheet for the red and the blue player, writes both, and checks that the unit draws and that its team color follows the
         * player color.
         *
         * @return the red player's sheet
         */
        private int @NotNull [] check(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, int scale, @NotNull ByteBuffer pixels,
                                      @NotNull Path output, @NotNull String mode) throws IOException {
            int[] red = this.draw(world, scene, scale, pixels, RED_PLAYER, true);
            int[] blue = this.draw(world, scene, scale, pixels, BLUE_PLAYER, true);
            int[] empty = this.draw(world, scene, scale, pixels, RED_PLAYER, false);

            writeSheet(red, this.getWidth() * scale, this.getHeight() * scale, output.resolve(this.name + "-" + mode + "-red.png"));
            writeSheet(blue, this.getWidth() * scale, this.getHeight() * scale, output.resolve(this.name + "-" + mode + "-blue.png"));
            Assertions.assertFalse(Arrays.equals(red, empty), mode + ": the unit draws pixels.");
            Assertions.assertFalse(Arrays.equals(red, blue), mode + ": the team color follows the player color.");

            return red;
        }

        /**
         * Draws the sheet cell by cell (a hidden window larger than the screen keeps only the part of its framebuffer on the screen) and
         * returns its pixels, rows from the bottom up.
         */
        private int @NotNull [] draw(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, int scale, @NotNull ByteBuffer pixels, int player,
                                     boolean unit) throws IOException {
            int width = this.getWidth() * scale;
            int height = this.getHeight() * scale;
            int rows = this.rowFrameSets.length;
            int tile = this.cell * scale;
            int[] result = new int[width * height];

            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < this.getColumns(); column++) {
                    this.drawCell(world, scene, scale, player, unit, column, row);
                    glReadPixels(0, 0, tile, tile, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                    for (int y = 0; y < tile; y++) {
                        for (int x = 0; x < tile; x++) {
                            result[((rows - 1 - row) * tile + y) * width + column * tile + x] = pixels.getInt((y * tile + x) * OverlayPass.RGBA_BYTES);
                        }
                    }
                }
            }

            return result;
        }

        /**
         * Draws the whole sheet with the camera on one cell.
         */
        private void drawCell(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, int scale, int player, boolean unit, int cellColumn,
                              int cellRow) throws IOException {
            int tile = this.cell * scale;

            world.beginFrame(tile, tile, tile, tile, cellColumn * this.cell, cellRow * this.cell, scale);

            try {
                scene.drawTerrain(index -> TILE);

                for (int column = 0; column < this.nativeImages.length; column++) {
                    drawNative(scene, this.nativeImages[column], this.nativeFrame, player, column * this.cell + this.cell / 2, this.cell / 2);
                }

                for (int row = 0; row < this.rowFrameSets.length && unit; row++) {
                    for (int column = 0; column < FACINGS.length; column++) {
                        int frame = this.rowFrameSets[row] * DIRECTIONS + FACINGS[column];
                        int x = (column + this.nativeImages.length) * this.cell + this.cell / 2;
                        int y = row * this.cell + this.cell / 2;
                        boolean flipped = column == FACINGS.length - 1;

                        scene.drawImage(this.shadowImage, frame, SceneRenderer.DRAW_FUNCTION_SHADOW, 0, player, x, y + this.shadowOffset, flipped);
                        scene.drawImage(this.mainImage, frame, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, player, x, y, flipped);

                        if (row == MYRIARCH_FIRE_ROW && this.overlayImage != NO_OVERLAY) {
                            int[] offset = this.overlayOffsets[column];

                            scene.drawImage(this.overlayImage, FIRE_FRAME_SET * DIRECTIONS + FACINGS[column], SceneRenderer.DRAW_FUNCTION_NORMAL, 0,
                                            player, x + (flipped ? -offset[0] : offset[0]), y + offset[1], flipped);
                        }
                    }
                }
            } finally {
                scene.finishScene();
                world.endFrame();
            }
        }
    }

    /**
     * Draws Unity-chan's sample sheets and checks them.
     *
     * @param installation the SC:R installation
     * @param output       the folder that receives the sheets {@code unitychan-<mode>-<player>.png}
     * @throws IOException if the installation or the crossover's graphics cannot be read, or a sheet cannot be written
     */
    public static void drawSamples(@NotNull Path installation, @NotNull Path output) throws IOException {
        drawSamples(installation, output, false);
    }

    private static void drawSamples(@NotNull Path installation, @NotNull Path output, boolean myriarch) throws IOException {
        int cell = myriarch ? MYRIARCH_CELL : UNITY_CHAN_CELL;

        try (ClientHost host = ClientHost.initialize()) {
            HostWindow window = host.createWindow(WindowSettings.builder("SC:R Crossover unit samples", cell * HD_PIXEL_SCALE,
                                                                         cell * HD_PIXEL_SCALE).build());

            window.makeContextCurrent();
            host.createCapabilities();
            drawSheets(installation, output, myriarch);
            Assertions.assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    /**
     * Draws the Myriarch's sample sheets beside a Zerg Hive and checks them.
     *
     * @param installation the SC:R installation
     * @param output       the folder that receives the sheets {@code myriarch-<mode>-<player>.png}
     * @throws IOException if the installation or the crossover's graphics cannot be read, or a sheet cannot be written
     */
    public static void drawMyriarchSamples(@NotNull Path installation, @NotNull Path output) throws IOException {
        drawSamples(installation, output, true);
    }

    private static void drawSheets(@NotNull Path installation, @NotNull Path output, boolean myriarch) throws IOException {
        int[] tiles = new int[MAP_TILES * MAP_TILES];

        Arrays.fill(tiles, TILE);

        try (CascStorage storage = CascStorage.open(installation); GameData gameData = GameData.loadFrom(installation);
             WorldRenderer world = new WorldRenderer()) {
            // A started game builds the game definition, which closes the registration of the added graphics.
            RunningGameScenario.start(gameData);

            ScrAddedGraphics graphics = gameData.getAddedGraphics();
            int shadowImage = ScrAddedGraphics.FIRST_IMAGE_ID + (myriarch ? MYRIARCH_IMAGES : 0);
            int mainImage = shadowImage + 1;
            int marineImage = findImage(gameData, ScrUnitIds.TERRAN_MARINE);
            int goliathImage = findImage(gameData, ScrUnitIds.TERRAN_GOLIATH);
            int[][] fireOffsets = new int[FACINGS.length][];

            for (int column = 0; column < FACINGS.length && myriarch; column++) {
                int offset = gameData.getImageOverlayOffset(mainImage, ATTACK_OVERLAY,
                                                            MYRIARCH_ROW_FRAME_SETS[MYRIARCH_FIRE_ROW] * DIRECTIONS + FACINGS[column],
                                                            BREATH_POSITION);

                fireOffsets[column] = new int[]{(short) offset, offset >> Short.SIZE};
            }

            Sheet sheet = myriarch ? new Sheet("myriarch", MYRIARCH_CELL, MYRIARCH_ROW_FRAME_SETS, mainImage, shadowImage,
                                               MYRIARCH_SHADOW_OFFSET, ScrAddedGraphics.FIRST_IMAGE_ID + FIRE_IMAGE, fireOffsets, 0,
                                               new int[]{NO_SHADOW, findImage(gameData, ScrUnitIds.ZERG_HIVE)})
                                   : new Sheet("unitychan", UNITY_CHAN_CELL, UNITY_CHAN_ROW_FRAME_SETS, mainImage, shadowImage,
                                               UNITY_CHAN_SHADOW_OFFSET, NO_OVERLAY, new int[0][], DIRECTIONS - 1,
                                               new int[]{findShadowImage(gameData, marineImage), marineImage},
                                               new int[]{findShadowImage(gameData, goliathImage), goliathImage,
                                                         findImage(gameData, ScrUnitIds.TERRAN_GOLIATH_TURRET)});
            ByteBuffer pixels = MemoryUtil.memAlloc(sheet.cell * HD_PIXEL_SCALE * sheet.cell * HD_PIXEL_SCALE * OverlayPass.RGBA_BYTES);

            Assertions.assertTrue(graphics.containsImage(mainImage));
            world.loadMap(ScrResourceProvider.borrow(storage, graphics), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);

            try {
                try (ClassicSceneRenderer scene = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage, graphics), JUNGLE_TILESET)) {
                    sheet.check(world, scene, 1, pixels, output, "sd");
                }

                HdSceneRenderer scene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage, graphics), JUNGLE_TILESET,
                                                                                       HdRenderAssets.Quality.HD));

                try {
                    world.setRenderScaleLimit(HdRenderAssets.Quality.HD.getScale());

                    int[] forward = sheet.check(world, scene, HD_PIXEL_SCALE, pixels, output, "hd");

                    scene.setLightingEnabled(true);

                    int[] lit = sheet.check(world, scene, HD_PIXEL_SCALE, pixels, output, "hd-lit");

                    Assertions.assertFalse(Arrays.equals(forward, lit), "Lighting changes the HD sheet.");
                } finally {
                    IgnoreException.execute(
                            scene::close
                    );
                }
            } finally {
                IgnoreException.execute(
                        () -> MemoryUtil.memFree(pixels)
                );
            }
        }
    }

    /**
     * Draws the direction sheet of the Myriarch and checks it: one row of a native Zerg Ultralisk (its first frame set) and one row per frame
     * set of {@link #DIRECTION_SHEET_FRAME_SETS} of the Myriarch, each in the 17 rendered directions (north, clockwise to south), every image
     * with its shadow, at the same scale, in the SD renderer and the HD renderer (HD2 quality); every direction of the Myriarch draws.
     *
     * @param installation the SC:R installation
     * @param output       the folder that receives {@code myriarch-directions-<mode>.png}
     * @throws IOException if the installation or the crossover's graphics cannot be read, or a sheet cannot be written
     */
    public static void drawDirections(@NotNull Path installation, @NotNull Path output) throws IOException {
        try (ClientHost host = ClientHost.initialize()) {
            HostWindow window = host.createWindow(WindowSettings.builder("SC:R Crossover directions", MYRIARCH_CELL * HD2_PIXEL_SCALE,
                                                                         MYRIARCH_CELL * HD2_PIXEL_SCALE).build());

            window.makeContextCurrent();
            host.createCapabilities();

            int[] tiles = new int[MAP_TILES * MAP_TILES];

            Arrays.fill(tiles, TILE);

            try (CascStorage storage = CascStorage.open(installation); GameData gameData = GameData.loadFrom(installation);
                 WorldRenderer world = new WorldRenderer()) {
                RunningGameScenario.start(gameData);

                ScrAddedGraphics graphics = gameData.getAddedGraphics();
                int ultraliskImage = findImage(gameData, ScrUnitIds.ZERG_ULTRALISK);
                int[][] rows = new int[DIRECTION_SHEET_FRAME_SETS.length + 1][];

                rows[0] = new int[]{findShadowImage(gameData, ultraliskImage), ultraliskImage, 0, 0};

                for (int row = 0; row < DIRECTION_SHEET_FRAME_SETS.length; row++) {
                    rows[row + 1] = new int[]{ScrAddedGraphics.FIRST_IMAGE_ID + MYRIARCH_IMAGES,
                                              ScrAddedGraphics.FIRST_IMAGE_ID + MYRIARCH_IMAGES + 1,
                                              DIRECTION_SHEET_FRAME_SETS[row], MYRIARCH_SHADOW_OFFSET};
                }

                ByteBuffer pixels = MemoryUtil.memAlloc(MYRIARCH_CELL * HD2_PIXEL_SCALE * MYRIARCH_CELL * HD2_PIXEL_SCALE * OverlayPass.RGBA_BYTES);

                world.loadMap(ScrResourceProvider.borrow(storage, graphics), JUNGLE_TILESET, MAP_TILES, MAP_TILES, tiles);

                try {
                    try (ClassicSceneRenderer scene = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage, graphics),
                                                                               JUNGLE_TILESET)) {
                        writeDirections(world, scene, 1, pixels, rows, output.resolve("myriarch-directions-sd.png"));
                    }

                    HdSceneRenderer scene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage, graphics), JUNGLE_TILESET,
                                                                                           HdRenderAssets.Quality.HD2));

                    try {
                        world.setRenderScaleLimit(HdRenderAssets.Quality.HD2.getScale());
                        writeDirections(world, scene, HD2_PIXEL_SCALE, pixels, rows, output.resolve("myriarch-directions-hd2.png"));
                    } finally {
                        IgnoreException.execute(
                                scene::close
                        );
                    }
                } finally {
                    IgnoreException.execute(
                            () -> MemoryUtil.memFree(pixels)
                    );
                }
            }

            Assertions.assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    /**
     * Draws the Myriarch of a live game of the clone as the game shows it (the world's sprites with their shadows, the selection circle and the
     * health bar, on the map's terrain): after the Hive morphs into it, it walks from the Hive's place to a clicked position, one cell every
     * {@link #WALK_SHOT_FRAMES} frames, then it dies, two cells of its death; in the SD renderer and the HD renderer. Checks that it moves from
     * cell to cell and that each cell draws, and writes {@code myriarch-walk-<mode>.png}.
     *
     * @param installation the SC:R installation
     * @param output       the folder that receives the sheets
     * @throws IOException if the installation or the crossover's graphics cannot be read, or a sheet cannot be written
     */
    public static void drawWalk(@NotNull Path installation, @NotNull Path output) throws IOException {
        try (ClientHost host = ClientHost.initialize()) {
            HostWindow window = host.createWindow(WindowSettings.builder("SC:R Crossover walk", MYRIARCH_CELL * HD_PIXEL_SCALE,
                                                                         MYRIARCH_CELL * HD_PIXEL_SCALE).build());

            window.makeContextCurrent();
            host.createCapabilities();
            drawWalkSheet(installation, output, false);
            drawWalkSheet(installation, output, true);
            Assertions.assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static void drawWalkSheet(@NotNull Path installation, @NotNull Path output, boolean hd) throws IOException {
        try (CascStorage storage = CascStorage.open(installation); GameData gameData = GameData.loadFrom(installation);
             WorldRenderer world = new WorldRenderer()) {
            CloneZergPlayer zerg = CloneMorphScenario.start(gameData, installation);
            Unit myriarch = CloneMorphScenario.morphToHive(zerg);
            int startX = myriarch.getPositionX();

            zerg.select(myriarch);
            zerg.morphBuilding(CloneMorphScenario.MYRIARCH_ID);
            CloneMorphScenario.runUntilType(zerg, myriarch, CloneMorphScenario.MYRIARCH_ID);

            GameMap map = zerg.getGame().getMap();
            ScrResourceProvider resources = ScrResourceProvider.borrow(storage, gameData.getAddedGraphics());
            int scale = hd ? HD_PIXEL_SCALE : 1;
            int tile = MYRIARCH_CELL * scale;
            int width = WALK_CELLS * tile;
            int[] sheet = new int[width * tile];
            ByteBuffer pixels = MemoryUtil.memAlloc(tile * tile * OverlayPass.RGBA_BYTES);
            SceneRenderer scene;
            Runnable closeScene;

            world.loadMap(resources, map.getTileset(), map.getWidth(), map.getHeight(), map.copyTileIdentifiersForRendering());

            if (hd) {
                HdSceneRenderer hdScene = new HdSceneRenderer(world, HdRenderAssets.load(resources, map.getTileset(), HdRenderAssets.Quality.HD));

                scene      = hdScene;
                closeScene = hdScene::close;
                world.setRenderScaleLimit(HdRenderAssets.Quality.HD.getScale());
            } else {
                ClassicSceneRenderer classicScene = new ClassicSceneRenderer(world, resources, map.getTileset());

                scene      = classicScene;
                closeScene = classicScene::close;
            }

            try {
                // The local selection shows the selected unit's circle and health bar.
                zerg.select(myriarch);
                RunningGameScenario.runInCommandDispatch(zerg.getRuntime(), () -> {
                    zerg.getGame().getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(0, myriarch.getIndex());
                    zerg.getGame().getSelectionImages().selectUnit(myriarch, 0);
                });
                zerg.rightClick(startX - WALK_DISTANCE, myriarch.getPositionY(), null);

                int previousX = startX;
                int cameraX = 0;
                int cameraY = 0;

                for (int cell = 0; cell < WALK_CELLS; cell++) {
                    // The walk's cells follow the unit; the death's stay where it died.
                    if (cell == WALK_CELLS - DEATH_CELLS) {
                        // The local selection lets the dying unit go, as the client's does.
                        RunningGameScenario.runInCommandDispatch(zerg.getRuntime(), () -> {
                            zerg.getGame().getSelectionImages().deselectUnit(myriarch);
                            zerg.getGame().getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(0, 0);
                            zerg.getGame().getUnitLifecycle().kill(myriarch, RunningGameScenario.INSTRUCTION_LIMIT);
                        });
                    }

                    CloneMorphScenario.runFrames(zerg, cell < WALK_CELLS - DEATH_CELLS ? WALK_SHOT_FRAMES : DEATH_SHOT_FRAMES);

                    if (cell < WALK_CELLS - DEATH_CELLS) {
                        int positionX = myriarch.getPositionX();

                        Assertions.assertTrue(positionX < previousX, "the Myriarch walks on: " + positionX + " after " + previousX);

                        previousX = positionX;
                        cameraX   = positionX - MYRIARCH_CELL / 2;
                        cameraY   = myriarch.getPositionY() - MYRIARCH_CELL / 2;
                    }

                    drawGameCell(world, scene, zerg, scale, cameraX, cameraY, pixels);

                    int first = pixels.getInt(0);
                    boolean drawn = false;

                    for (int y = 0; y < tile; y++) {
                        for (int x = 0; x < tile; x++) {
                            int value = pixels.getInt((y * tile + x) * OverlayPass.RGBA_BYTES);

                            sheet[y * width + cell * tile + x] = value;
                                                                 drawn |= value != first;
                        }
                    }

                    Assertions.assertTrue(drawn, "cell " + cell + " draws");
                }

                writeSheet(sheet, width, tile, output.resolve("myriarch-walk-" + (hd ? "hd" : "sd") + ".png"));
            } finally {
                IgnoreException.execute(
                        closeScene::run,
                        () -> MemoryUtil.memFree(pixels)
                );
            }
        }
    }

    /**
     * Draws the game's terrain and sprites, as the game shows them, with the camera at a world position.
     */
    private static void drawGameCell(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, @NotNull CloneZergPlayer zerg, int scale,
                                     int cameraX, int cameraY, @NotNull ByteBuffer pixels) throws IOException {
        int tile = MYRIARCH_CELL * scale;
        int[] tiles = zerg.getGame().getMap().copyTileIdentifiersForRendering();
        List<String> warnings = new ArrayList<>();

        world.beginFrame(tile, tile, tile, tile, cameraX, cameraY, scale);

        try {
            scene.drawTerrain(index -> tiles[index]);
            new WorldSceneExtraction().draw(scene, world, new ScrGameView(zerg.getGame()), null, owner -> owner, warnings::add);
        } finally {
            scene.finishScene();
            world.endFrame();
        }

        glReadPixels(0, 0, tile, tile, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        Assertions.assertEquals(List.of(), warnings);
    }

    /**
     * Draws the direction sheet cell by cell (each row's shadow image, main image, frame set and shadow offset; one column per direction) and writes
     * it; checks that every cell of the Myriarch's rows draws.
     */
    private static void writeDirections(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, int scale, @NotNull ByteBuffer pixels,
                                        int @NotNull [] @NotNull [] rows, @NotNull Path file) throws IOException {
        int tile = MYRIARCH_CELL * scale;
        int width = DIRECTIONS * tile;
        int height = rows.length * tile;
        int[] sheet = new int[width * height];

        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < DIRECTIONS; column++) {
                world.beginFrame(tile, tile, tile, tile, column * MYRIARCH_CELL, row * MYRIARCH_CELL, scale);

                try {
                    scene.drawTerrain(index -> TILE);

                    for (int cellRow = 0; cellRow < rows.length; cellRow++) {
                        for (int cellColumn = 0; cellColumn < DIRECTIONS; cellColumn++) {
                            int frame = rows[cellRow][ROW_FRAME_SET] * DIRECTIONS + cellColumn;
                            int x = cellColumn * MYRIARCH_CELL + MYRIARCH_CELL / 2;
                            int y = cellRow * MYRIARCH_CELL + MYRIARCH_CELL / 2;

                            scene.drawImage(rows[cellRow][ROW_SHADOW_IMAGE], frame, SceneRenderer.DRAW_FUNCTION_SHADOW, 0, RED_PLAYER,
                                            x, y + rows[cellRow][ROW_SHADOW_OFFSET], false);
                            scene.drawImage(rows[cellRow][ROW_MAIN_IMAGE], frame, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, RED_PLAYER, x, y, false);
                        }
                    }
                } finally {
                    scene.finishScene();
                    world.endFrame();
                }

                glReadPixels(0, 0, tile, tile, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

                int[] cell = new int[tile * tile];

                for (int y = 0; y < tile; y++) {
                    for (int x = 0; x < tile; x++) {
                        cell[y * tile + x]                                                      = pixels.getInt((y * tile + x) * OverlayPass.RGBA_BYTES);
                        sheet[((rows.length - 1 - row) * tile + y) * width + column * tile + x] = cell[y * tile + x];
                    }
                }

                if (row > 0) {
                    int first = cell[0];

                    Assertions.assertTrue(Arrays.stream(cell).anyMatch(value -> value != first), "direction " + column + " of row " + row + " draws");
                }
            }
        }

        writeSheet(sheet, width, height, file);
    }

    private static int findImage(@NotNull GameData gameData, int unitType) {
        return gameData.getSpriteTypes().getImage(gameData.getFlingyTypes().getSprite(gameData.getUnitTypes().getFlingyType(unitType)));
    }

    /**
     * Draws a native unit with a frame: its shadow image (if any), then its other images in order.
     */
    private static void drawNative(@NotNull SceneRenderer scene, int @NotNull [] images, int frame, int player, int x, int y) throws IOException {
        if (images[0] != NO_SHADOW) {
            scene.drawImage(images[0], frame, SceneRenderer.DRAW_FUNCTION_SHADOW, 0, player, x, y, false);
        }

        for (int index = 1; index < images.length; index++) {
            scene.drawImage(images[index], frame, SceneRenderer.DRAW_FUNCTION_NORMAL, 0, player, x, y, false);
        }
    }

    /**
     * Finds the shadow image that the installed script of an image creates in its {@code Init} animation.
     */
    private static int findShadowImage(@NotNull GameData gameData, int image) {
        IscriptHeader header = gameData.getIscript().findHeader(gameData.getImageTypes().getIscript(image));

        Assertions.assertNotNull(header);

        for (IscriptHeader.Animation animation : header.getAnimations()) {
            for (IscriptInstruction instruction : animation.getInstructions()) {
                if (animation.getTypeIndex() == INIT_ANIMATION && instruction.getOpcode() == IscriptOpcodes.IMGUL) {
                    return instruction.getOperands()[0];
                }
            }
        }

        return Assertions.fail("The script of image " + image + " creates no shadow image.");
    }

    private static void writeSheet(int @NotNull [] sheet, int width, int height, @NotNull Path file) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = sheet[(height - 1 - y) * width + x];
                int red = rgba & OPAQUE_ALPHA;
                int green = rgba >>> Byte.SIZE & OPAQUE_ALPHA;
                int blue = rgba >>> Short.SIZE & OPAQUE_ALPHA;

                image.setRGB(x, y, OPAQUE_ALPHA << ALPHA_SHIFT | red << Short.SIZE | green << Byte.SIZE | blue);
            }
        }

        Files.createDirectories(file.toAbsolutePath().getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private CloneRenderScenario() {
    }
}
