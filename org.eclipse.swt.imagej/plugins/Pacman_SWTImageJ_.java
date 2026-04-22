/**
 * PACMAN SWTImageJ - ImageJ Plugin with SWT UI
 *
 * GENERAL DESCRIPTION:
 * An SWTImageJ version of the classic Pacman plugin.
 * Unlike the AWT-based Pacman_ImageJ_, this plugin uses SWT (Standard Widget Toolkit)
 * for all windowing and rendering.  It implements ij.plugin.PlugIn so ImageJ can
 * discover and launch it via the standard plugin menu, while delegating all UI work
 * to the SWT Display provided by the Bio7 / SWTImageJ host environment.
 *
 * Key differences from Pacman_SWT (standalone):
 * - Implements ij.plugin.PlugIn; entry point is run(String arg) not main().
 * - Uses Display.getDefault() – the shared SWT Display already running inside the
 *   Bio7 / SWTImageJ host – instead of creating its own Display.
 * - Does NOT run a private SWT event loop; the host event loop drives the shell.
 * - Shell creation is marshalled to the SWT thread via display.syncExec().
 * - SWT resources are disposed in a DisposeListener when the shell closes.
 *
 * Features:
 * - 4 game levels with different maze layouts
 * - Pacman movement and animation
 * - Ghost AI and collision detection
 * - Power-pellet system with invincibility timer
 * - Score, lives, and level tracking
 * - Tunnel wrapping mechanics
 *
 * CONTROLS:
 * [S]          - Start Game
 * [P]          - Pause / Resume
 * [Arrow Keys] - Move Pacman
 * [ESC]        - Close window
 */

import ij.plugin.PlugIn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.util.ArrayList;

public class Pacman_SWTImageJ_ implements PlugIn {

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------
    private static final int TILE    = 26;
    private static final int ROWS    = 21;
    private static final int COLS    = 21;
    private static final int ARENA_W = COLS * TILE;        // 546
    private static final int SIDE_W  = 160;
    private static final int W       = ARENA_W + SIDE_W;   // 706
    private static final int H       = ROWS * TILE;        // 546
    /** Freeze duration (ms) after Pacman loses a life. */
    private static final int LIFE_LOST_DELAY_MS      = 600;
    /** Freeze duration (ms) after completing a level. */
    private static final int LEVEL_COMPLETE_DELAY_MS = 1000;

    // ---------------------------------------------------------------------------
    // Game state
    // ---------------------------------------------------------------------------
    private int score = 0, lives = 3, level = 1, powerTimer = 0;
    private final int[][] map = new int[ROWS][COLS];
    private boolean running = true, paused = false, gameStarted = false;
    private boolean up, down, left, right;
    private int frame = 0;
    /** When true, game logic is suspended temporarily (life-lost / level-complete pause). */
    private boolean frozen = false;

    // ---------------------------------------------------------------------------
    // Entities
    // ---------------------------------------------------------------------------
    static class Entity {
        int x, y, dx, dy, nextDx, nextDy;
        Entity(int cx, int cy) { x = cx * TILE; y = cy * TILE; }
    }

    private Entity pac;
    private final ArrayList<Entity> ghosts = new ArrayList<>();

    // ---------------------------------------------------------------------------
    // SWT fields
    // ---------------------------------------------------------------------------
    private Display display;
    private Shell   shell;
    private Canvas  canvas;

    // SWT Colors and Fonts – allocated once, disposed via DisposeListener
    private Color colBlack, colDarkBlue, colLightBlue, colWhite,
                  colYellow, colRed, colPink, colCyan, colOrange, colBlue;
    private Font fontBig, fontHud;

    // ---------------------------------------------------------------------------
    // ImageJ PlugIn entry point
    // ---------------------------------------------------------------------------
    @Override
    public void run(String arg) {
        // Obtain the shared SWT Display from the Bio7 / SWTImageJ host.
        // Display.getDefault() returns the existing Display if one is already running
        // on the current thread, or creates one if this is the first call.
        display = Display.getDefault();

        // Marshal all SWT UI construction onto the SWT thread.
        // syncExec blocks until the Runnable completes, so run() returns only after
        // the shell has been created and the game loop started.
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                createAndOpenShell();
            }
        });
        // run() returns here; the host's SWT event loop keeps the shell alive.
    }

    // ---------------------------------------------------------------------------
    // Shell / canvas construction (must be called on the SWT thread)
    // ---------------------------------------------------------------------------
    private void createAndOpenShell() {
        shell = new Shell(display);
        shell.setText("Pacman SWTImageJ - Level " + level);
        shell.setLayout(new FillLayout());

        allocateResources();
        setupLevel(true);

        canvas = new Canvas(shell, SWT.NO_BACKGROUND | SWT.DOUBLE_BUFFERED);
        canvas.setSize(W, H);

        // Paint listener – renders one frame on every redraw request
        canvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                renderFrame(e.gc);
            }
        });

        // Keyboard input
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.keyCode) {
                    case 's': case 'S': gameStarted = true; break;
                    case 'p': case 'P': paused = !paused;   break;
                    case SWT.ARROW_UP:    up=true;  down=false; left=false;  right=false; break;
                    case SWT.ARROW_DOWN:  up=false; down=true;  left=false;  right=false; break;
                    case SWT.ARROW_LEFT:  up=false; down=false; left=true;   right=false; break;
                    case SWT.ARROW_RIGHT: up=false; down=false; left=false;  right=true;  break;
                    case SWT.ESC:         running = false; shell.close(); break;
                    default: break;
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.keyCode) {
                    case SWT.ARROW_UP:    up    = false; break;
                    case SWT.ARROW_DOWN:  down  = false; break;
                    case SWT.ARROW_LEFT:  left  = false; break;
                    case SWT.ARROW_RIGHT: right = false; break;
                    default: break;
                }
            }
        });

        // Dispose SWT resources when the shell is closed
        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                running = false;
                disposeResources();
            }
        });

        Point preferred = shell.computeSize(W, H);
        shell.setSize(preferred);
        shell.open();
        canvas.setFocus();

        // Start the non-blocking game loop
        scheduleGameLoop();
    }

    // ---------------------------------------------------------------------------
    // Game loop via display.timerExec (40 ms ≈ 25 fps)
    // The host SWT event loop drives execution; we never block the display thread.
    // ---------------------------------------------------------------------------
    private void scheduleGameLoop() {
        display.timerExec(40, new Runnable() {
            @Override
            public void run() {
                if (shell.isDisposed() || !running) return;

                if (gameStarted && !paused && !frozen) {
                    handleMovement();
                    updateLogic();
                    checkLevelComplete();
                }

                frame++;
                if (gameStarted && !paused && powerTimer > 0) powerTimer--;

                if (lives <= 0) {
                    gameStarted = false;
                    setupLevel(true);
                    shell.setText("GAME OVER – Final Score: " + score);
                }

                if (!canvas.isDisposed()) {
                    canvas.redraw();
                }

                scheduleGameLoop(); // re-schedule for the next tick
            }
        });
    }

    // ---------------------------------------------------------------------------
    // Level data
    // ---------------------------------------------------------------------------
    private String[] getLevelData(int lvl) {
        String[][] mazes = {
            { // --- LEVEL 1: CLASSIC ---
                "#####################",
                "#.........#.........#",
                "#.###.###.#.###.###.#",
                "#*# #.# #.#.# #.# #*#",
                "#.###.###.#.###.###.#",
                "#...................#",
                "#.###.#.#####.#.###.#",
                "#.....#...#...#.....#",
                "#####.### # ###.#####",
                "    #.#  GGG  #.#    ",
                "      .  GGG  .      ",
                "#####.# ##### #.#####",
                "    #.#       #.#    ",
                "#####.# ##### #.#####",
                "#.........#.........#",
                "#.###.###.#.###.###.#",
                "#*..#.....P.....#..*#",
                "###.#.#.#####.#.#.###",
                "#.....#...#...#.....#",
                "#.#################.#",
                "#####################"
            },
            { // --- LEVEL 2: THE HUB ---
                "#####################",
                "#*........#........*#",
                "#.#######.#.#######.#",
                "#.#######.#.#######.#",
                "#.........#.........#",
                "#.###.#########.###.#",
                "#.#...#.......#...#.#",
                "#.#.#.#.#####.#.#.#.#",
                "#...#...........#...#",
                "###.#.### G ###.#.###",
                "      .  GGG  .      ",
                "###.#.#########.#.###",
                "#...#...........#...#",
                "#.###.#########.###.#",
                "#.........#.........#",
                "#.#######.#.#######.#",
                "#*......#.P.#......*#",
                "#######.#.#.#.#######",
                "#.......#...#.......#",
                "#.#################.#",
                "#####################"
            },
            { // --- LEVEL 3: THE CROSS ---
                "#####################",
                "#.........#.........#",
                "#*#######...#######*#",
                "#.#######.#.#######.#",
                "#.........#.........#",
                "#####.#########.#####",
                "    #.#  GGG  #.#    ",
                "#####.# ##### #.#####",
                "#.........#.........#",
                "#.#######.#.#######.#",
                "      .   P   .      ",
                "#.#######.#.#######.#",
                "#.........#.........#",
                "#####.# ##### #.#####",
                "    #.#       #.#    ",
                "#####.#########.#####",
                "#.........#.........#",
                "#.#######.#.#######.#",
                "#*#######...#######*#",
                "#.........G.........#",
                "#####################"
            },
            { // --- LEVEL 4: THE BIG P ---
                "#####################",
                "#*.......###.......*#",
                "#.######.###.######.#",
                "#.######.....######.#",
                "#.#      ###      #.#",
                "#.# #####   ##### #.#",
                "#.# #   # G #   # #.#",
                "#.# # ####### # # #.#",
                "#.# # #     # # # #.#",
                "      # ##### #      ",
                "#.# # # G G G # # #.#",
                "#.# # # ##### # # #.#",
                "#.# # #       # # #.#",
                "#.# # ######### # #.#",
                "#.# #           # #.#",
                "#.# ############# #.#",
                "#*...............P..#",
                "###.#############.###",
                "#...................#",
                "#.#################.#",
                "#####################"
            }
        };
        return mazes[(lvl - 1) % mazes.length];
    }

    // ---------------------------------------------------------------------------
    // Game logic
    // ---------------------------------------------------------------------------
    private void setupLevel(boolean resetAll) {
        if (resetAll) { score = 0; lives = 3; level = 1; }
        powerTimer = 0;
        String[] layout = getLevelData(level);
        for (int r = 0; r < ROWS; r++) {
            String line = layout[r];
            for (int c = 0; c < COLS; c++) {
                char tile = (c < line.length()) ? line.charAt(c) : ' ';
                if      (tile == '#') map[r][c] = 0;
                else if (tile == '.') map[r][c] = 2;
                else if (tile == '*') map[r][c] = 3;
                else                  map[r][c] = 1;
                if (tile == 'P') pac = new Entity(c, r);
            }
        }
        ghosts.clear();
        // All ghosts start near the centre of the grid (tile 10,10). The 'G' / 'GGG'
        // markers in the maze strings are purely decorative – they document where the
        // ghost-house is located visually but are not parsed for spawn coordinates,
        // matching the behaviour of the original ImageJ plugin.
        for (int i = 0; i < 4; i++) ghosts.add(new Entity(10, 10));
        up = down = left = right = false;
        if (shell != null && !shell.isDisposed()) {
            shell.setText("Pacman SWTImageJ - Level " + level);
        }
    }

    private void handleMovement() {
        if      (up)    { pac.nextDx =  0; pac.nextDy = -1; }
        else if (down)  { pac.nextDx =  0; pac.nextDy =  1; }
        else if (left)  { pac.nextDx = -1; pac.nextDy =  0; }
        else if (right) { pac.nextDx =  1; pac.nextDy =  0; }
    }

    private void updateLogic() {
        // Tunnel wrapping for Pacman
        if (pac.x <= -TILE)        pac.x = ARENA_W - 2;
        else if (pac.x >= ARENA_W) pac.x = -TILE + 2;

        if (pac.x % TILE == 0 && pac.y % TILE == 0) {
            if (canMove(pac.x, pac.y, pac.nextDx, pac.nextDy)) {
                pac.dx = pac.nextDx; pac.dy = pac.nextDy;
            } else if (!canMove(pac.x, pac.y, pac.dx, pac.dy)) {
                pac.dx = 0; pac.dy = 0;
            }
            int r = pac.y / TILE, c = pac.x / TILE;
            if (c >= 0 && c < COLS && r >= 0 && r < ROWS) {
                if (map[r][c] == 2) { map[r][c] = 1; score += 10; }
                if (map[r][c] == 3) { map[r][c] = 1; score += 50; powerTimer = 150; }
            }
        }
        pac.x += pac.dx * 2;
        pac.y += pac.dy * 2;

        for (Entity g : ghosts) {
            // Tunnel wrapping for ghosts
            if (g.x <= -TILE)        g.x = ARENA_W - 2;
            else if (g.x >= ARENA_W) g.x = -TILE + 2;

            if (g.x % TILE == 0 && g.y % TILE == 0) {
                int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
                ArrayList<int[]> pos = new ArrayList<>();
                for (int[] d : dirs) if (canMove(g.x, g.y, d[0], d[1])) pos.add(d);
                if (!pos.isEmpty()) {
                    int[] p = pos.get((int)(Math.random() * pos.size()));
                    g.dx = p[0]; g.dy = p[1];
                }
            }
            int s = (powerTimer > 0) ? 1 : 2;
            g.x += g.dx * s;
            g.y += g.dy * s;

            if (Math.abs(pac.x - g.x) < TILE * 0.7 && Math.abs(pac.y - g.y) < TILE * 0.7) {
                if (powerTimer > 0) {
                    g.x = 10 * TILE; g.y = 10 * TILE;
                    score += 200;
                } else {
                    lives--;
                    setupLevel(false);
                    // Freeze the game loop for LIFE_LOST_DELAY_MS (non-blocking)
                    frozen = true;
                    display.timerExec(LIFE_LOST_DELAY_MS, () -> frozen = false);
                    return;
                }
            }
        }
    }

    private boolean canMove(int x, int y, int dx, int dy) {
        int nx = (x / TILE) + dx;
        int ny = (y / TILE) + dy;
        if (nx < 0 || nx >= COLS) return (ny >= 0 && ny < ROWS);
        if (ny < 0 || ny >= ROWS) return false;
        return map[ny][nx] != 0;
    }

    private void checkLevelComplete() {
        boolean dots = false;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (map[r][c] > 1) { dots = true; break; }
        if (!dots) {
            level++;
            setupLevel(false);
            // Freeze the game loop for LEVEL_COMPLETE_DELAY_MS (non-blocking)
            frozen = true;
            display.timerExec(LEVEL_COMPLETE_DELAY_MS, () -> frozen = false);
        }
    }

    // ---------------------------------------------------------------------------
    // Rendering with SWT GC
    // ---------------------------------------------------------------------------
    private void renderFrame(GC gc) {
        // -- Background --
        gc.setBackground(colBlack);
        gc.fillRectangle(0, 0, W, H);

        // -- Walls --
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (map[r][c] == 0) {
                    gc.setBackground(colDarkBlue);
                    gc.fillRectangle(c * TILE, r * TILE, TILE, TILE);
                    gc.setForeground(colLightBlue);
                    gc.drawRectangle(c * TILE + 3, r * TILE + 3, TILE - 6, TILE - 6);
                }
            }
        }

        if (!gameStarted) {
            // -- Start screen --
            gc.setFont(fontBig);
            gc.setForeground(colYellow);
            gc.drawText("PACMAN SWTImageJ",    ARENA_W / 2 - 120, H / 2 - 50, true);
            gc.drawText("'S' TO START",         ARENA_W / 2 - 100, H / 2 - 20, true);
            gc.drawText("Arrow Keys: Move",     ARENA_W / 2 - 120, H / 2 + 20, true);
            gc.drawText("P: Pause   ESC: Quit", ARENA_W / 2 - 140, H / 2 + 50, true);
        } else {
            // -- Dots and power pellets --
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (map[r][c] == 2) {
                        gc.setBackground(colWhite);
                        gc.fillOval(c * TILE + 11, r * TILE + 11, 4, 4);
                    } else if (map[r][c] == 3) {
                        gc.setBackground(colWhite);
                        gc.fillOval(c * TILE + 7, r * TILE + 7, 12, 12);
                    }
                }
            }

            // -- Pacman --
            int open = (frame % 6 < 3) ? 35 : 0;
            int ang  = (pac.dx < 0) ? 180 : (pac.dy < 0) ? 90 : (pac.dy > 0) ? 270 : 0;
            gc.setBackground(colYellow);
            // SWT fillArc: angles in degrees, counter-clockwise from 3 o'clock
            gc.fillArc(pac.x + 2, pac.y + 2, TILE - 4, TILE - 4, ang + open, 360 - 2 * open);

            // -- Ghosts --
            Color[] ghostColors = { colRed, colPink, colCyan, colOrange };
            for (int i = 0; i < ghosts.size(); i++) {
                Entity g = ghosts.get(i);

                Color col;
                if (powerTimer > 0) {
                    col = (powerTimer < 40 && frame % 4 < 2) ? colWhite : colBlue;
                } else {
                    col = ghostColors[i % 4];
                }

                // Ghost body – top arc + rectangle
                gc.setBackground(col);
                gc.fillArc(g.x + 3, g.y + 2, TILE - 6, TILE - 8, 0, 180);
                gc.fillRectangle(g.x + 3, g.y + 2 + (TILE - 8) / 2, TILE - 6, 10);

                // Ghost eyes – white outer
                gc.setBackground(colWhite);
                gc.fillOval(g.x + 6  + g.dx * 2, g.y + 6  + g.dy * 2, 6, 6);
                gc.fillOval(g.x + 14 + g.dx * 2, g.y + 6  + g.dy * 2, 6, 6);
                // Ghost eyes – dark pupil
                gc.setBackground(colBlack);
                gc.fillOval(g.x + 8  + g.dx * 4, g.y + 8  + g.dy * 4, 3, 3);
                gc.fillOval(g.x + 16 + g.dx * 4, g.y + 8  + g.dy * 4, 3, 3);
            }

            // -- HUD (score / lives / level) --
            gc.setFont(fontHud);
            gc.setForeground(colWhite);
            gc.drawText("SCORE: " + score, ARENA_W + 20, 40,  true);
            gc.drawText("LIVES: " + lives, ARENA_W + 20, 70,  true);
            gc.drawText("LEVEL: " + level, ARENA_W + 20, 100, true);

            if (paused) {
                gc.setForeground(colYellow);
                gc.drawText("PAUSED", ARENA_W + 20, 140, true);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // SWT resource allocation / disposal
    // ---------------------------------------------------------------------------
    private void allocateResources() {
        colBlack     = new Color(display,   0,   0,   0);
        colDarkBlue  = new Color(display,   0,   0, 150);
        colLightBlue = new Color(display,   0, 180, 255);
        colWhite     = new Color(display, 255, 255, 255);
        colYellow    = new Color(display, 255, 255,   0);
        colRed       = new Color(display, 255,   0,   0);
        colPink      = new Color(display, 255, 175, 175);
        colCyan      = new Color(display,   0, 255, 255);
        colOrange    = new Color(display, 255, 165,   0);
        colBlue      = new Color(display,   0,   0, 255);
        fontBig      = new Font(display, "Monospaced", 18, SWT.BOLD);
        fontHud      = new Font(display, "Monospaced", 14, SWT.BOLD);
    }

    /** Called from the DisposeListener when the shell is closed. */
    private void disposeResources() {
        if (colBlack     != null && !colBlack.isDisposed())     colBlack.dispose();
        if (colDarkBlue  != null && !colDarkBlue.isDisposed())  colDarkBlue.dispose();
        if (colLightBlue != null && !colLightBlue.isDisposed()) colLightBlue.dispose();
        if (colWhite     != null && !colWhite.isDisposed())     colWhite.dispose();
        if (colYellow    != null && !colYellow.isDisposed())    colYellow.dispose();
        if (colRed       != null && !colRed.isDisposed())       colRed.dispose();
        if (colPink      != null && !colPink.isDisposed())      colPink.dispose();
        if (colCyan      != null && !colCyan.isDisposed())      colCyan.dispose();
        if (colOrange    != null && !colOrange.isDisposed())    colOrange.dispose();
        if (colBlue      != null && !colBlue.isDisposed())      colBlue.dispose();
        if (fontBig      != null && !fontBig.isDisposed())      fontBig.dispose();
        if (fontHud      != null && !fontHud.isDisposed())      fontHud.dispose();
    }
}
