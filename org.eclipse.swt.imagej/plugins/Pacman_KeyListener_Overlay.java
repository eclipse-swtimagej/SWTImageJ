import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import ij.plugin.*;
import java.util.ArrayList;

/**
 * Pacman for ImageJ using the KeyListener interface.
 * Features: Correct mouth direction, Power-Mode, and Ghost eyes.
 */
public class Pacman_KeyListener_Overlay implements PlugIn, KeyListener {
    private final int TILE = 26, ROWS = 21, COLS = 21;
    private final int ARENA_W = COLS * TILE, SIDE_W = 160;
    private final int W = ARENA_W + SIDE_W, H = ROWS * TILE;
    
    private int score = 0, lives = 3, level = 1, powerTimer = 0;
    private int[][] map = new int[ROWS][COLS];
    private boolean running = true, up, down, left, right;
    private int frame = 0;

    class Entity {
        int x, y, dx, dy, nextDx, nextDy;
        Entity(int cx, int cy) { x = cx * TILE; y = cy * TILE; }
    }

    private Entity pac;
    private ArrayList<Entity> ghosts = new ArrayList<>();

    public void run(String arg) {
        IJ.resetEscape();
        setupLevel(true);
        ColorProcessor cp = new ColorProcessor(W, H);
        drawWalls(cp);
        ImagePlus imp = new ImagePlus("Pacman KeyListener Fixed", cp);
        imp.show();

        // Register KeyListener on both Window and Canvas
        ImageWindow win = imp.getWindow();
        ImageCanvas canvas = win.getCanvas();
        win.addKeyListener(this);
        canvas.addKeyListener(this);
        
        // Ensure focus on click
        canvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { canvas.requestFocus(); }
        });
        canvas.requestFocus();

        while (running && !IJ.escapePressed()) {
            if (win == null || win.isClosed()) break;
            
            handleMovement();
            updateLogic();
            updateOverlay(imp);
            
            IJ.wait(40);
            frame++;
            if (powerTimer > 0) powerTimer--;
            
            if (lives <= 0) {
                IJ.showMessage("Game Over", "Score: " + score);
                setupLevel(true);
                drawWalls(cp);
            }
        }
    }

    private void setupLevel(boolean reset) {
        if (reset) { score = 0; lives = 3; level = 1; }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                map[r][c] = (r == 0 || r == ROWS - 1 || c == 0 || c == COLS - 1 || (r%2==0 && c%2==0)) ? 0 : 2;
            }
        }
        map[1][1] = 3; map[1][COLS-2] = 3; map[ROWS-2][1] = 3; map[ROWS-2][COLS-2] = 3;
        pac = new Entity(10, 15);
        ghosts.clear();
        for (int i = 0; i < 3; i++) ghosts.add(new Entity(10, 9));
        up=down=left=right=false;
    }

    private void handleMovement() {
        if (up) { pac.nextDx = 0; pac.nextDy = -1; }
        else if (down) { pac.nextDx = 0; pac.nextDy = 1; }
        else if (left) { pac.nextDx = -1; pac.nextDy = 0; }
        else if (right) { pac.nextDx = 1; pac.nextDy = 0; }
    }

    private void updateLogic() {
        if (pac.x % TILE == 0 && pac.y % TILE == 0) {
            if (canMove(pac.x, pac.y, pac.nextDx, pac.nextDy)) { pac.dx = pac.nextDx; pac.dy = pac.nextDy; }
            else if (!canMove(pac.x, pac.y, pac.dx, pac.dy)) { pac.dx = 0; pac.dy = 0; }
            int r = pac.y/TILE, c = pac.x/TILE;
            if (map[r][c] == 2) { map[r][c] = 1; score += 10; }
            if (map[r][c] == 3) { map[r][c] = 1; score += 50; powerTimer = 150; }
        }
        pac.x += pac.dx * 2; pac.y += pac.dy * 2;

        for (Entity g : ghosts) {
            if (g.x % TILE == 0 && g.y % TILE == 0) {
                if (!canMove(g.x, g.y, g.dx, g.dy) || Math.random() < 0.2) {
                    int[][] dirs = {{0,1}, {0,-1}, {1,0}, {-1,0}};
                    ArrayList<int[]> possible = new ArrayList<>();
                    for(int[] d : dirs) if (canMove(g.x, g.y, d[0], d[1])) possible.add(d);
                    if (!possible.isEmpty()) {
                        int[] pick = possible.get((int)(Math.random() * possible.size()));
                        g.dx = pick[0]; g.dy = pick[1];
                    }
                }
            }
            g.x += g.dx * 2; g.y += g.dy * 2;
            if (Math.abs(pac.x - g.x) < TILE*0.6 && Math.abs(pac.y - g.y) < TILE*0.6) {
                if (powerTimer > 0) { g.x = 10*TILE; g.y = 9*TILE; score += 200; }
                else { lives--; pac.x = 10*TILE; pac.y = 15*TILE; IJ.wait(600); }
            }
        }
    }

    private boolean canMove(int x, int y, int dx, int dy) {
        if (dx == 0 && dy == 0) return false;
        int nx = (x / TILE) + dx;
        int ny = (y / TILE) + dy;
        return nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS && map[ny][nx] != 0;
    }

    private void drawWalls(ColorProcessor cp) {
        cp.setColor(Color.BLACK); cp.fill();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (map[r][c] == 0) {
                    cp.setColor(new Color(0, 0, 120)); cp.fillRect(c*TILE, r*TILE, TILE, TILE);
                    cp.setColor(new Color(0, 150, 255)); cp.drawRect(c*TILE+3, r*TILE+3, TILE-6, TILE-6);
                }
            }
        }
    }

    private void updateOverlay(ImagePlus imp) {
        Overlay ov = new Overlay();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (map[r][c] == 2) {
                    ov.add(new OvalRoi(c*TILE+11, r*TILE+11, 4, 4) {{ setFillColor(new Color(255,184,151)); }});
                } else if (map[r][c] == 3) {
                    ov.add(new OvalRoi(c*TILE+7, r*TILE+7, 12, 12) {{ setFillColor(Color.WHITE); }});
                }
            }
        }

        int open = (frame % 6 < 3) ? 35 : 0;
        int startAngle = (pac.dx < 0) ? 180 : (pac.dy < 0) ? 90 : (pac.dy > 0) ? 270 : 0;
        ShapeRoi pShape = new ShapeRoi(new Arc2D.Double(pac.x+2, pac.y+2, TILE-4, TILE-4, startAngle + open, 360 - open*2, Arc2D.PIE));
        pShape.setFillColor(Color.YELLOW); ov.add(pShape);

        Color[] gCols = {Color.RED, Color.PINK, Color.CYAN, Color.ORANGE};
        for (int i=0; i<ghosts.size(); i++) {
            Entity g = ghosts.get(i);
            Color gc = (powerTimer > 0) ? (powerTimer < 40 && frame % 4 < 2 ? Color.WHITE : Color.BLUE) : gCols[i % 4];
            OvalRoi body = new OvalRoi(g.x+3, g.y+3, TILE-6, TILE-6); body.setFillColor(gc); ov.add(body);
            Roi skirt = new Roi(g.x+3, g.y+3+(TILE-6)/2, TILE-6, (TILE-6)/2); skirt.setFillColor(gc); ov.add(skirt);
            ov.add(new OvalRoi(g.x+6+g.dx*3, g.y+7+g.dy*2, 6, 7) {{ setFillColor(Color.WHITE); }});
            ov.add(new OvalRoi(g.x+14+g.dx*3, g.y+7+g.dy*2, 6, 7) {{ setFillColor(Color.WHITE); }});
        }
        TextRoi txt = new TextRoi(ARENA_W + 20, 40, "SCORE: " + score + "\nLIVES: " + lives + (powerTimer > 0 ? "\n\nPOWER!" : ""));
        txt.setStrokeColor(Color.CYAN); txt.setFont(new Font("Monospaced", Font.BOLD, 20));
        ov.add(txt);
        imp.setOverlay(ov);
    }

    // KeyListener Implementation
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) { up=true; down=false; left=false; right=false; }
        if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) { up=false; down=true; left=false; right=false; }
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) { up=false; down=false; left=true; right=false; }
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) { up=false; down=false; left=false; right=true; }
    }
    public void keyReleased(KeyEvent e) {}
    public void keyTyped(KeyEvent e) {}
}
