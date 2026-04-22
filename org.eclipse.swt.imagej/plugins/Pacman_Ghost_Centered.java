import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import ij.plugin.*;
import java.util.ArrayList;

public class Pacman_Ghost_Centered implements PlugIn {
    private final int TILE = 24, ROWS = 21, COLS = 21;
    private final int ARENA_W = COLS * TILE, SIDE_W = 160;
    private final int W = ARENA_W + SIDE_W, H = ROWS * TILE;
    
    private int score = 0, level = 1, lives = 3, powerTimer = 0;
    private int[][] map = new int[ROWS][COLS];
    private boolean running = true, up, down, left, right;
    private int frame = 0;

    class Entity {
        double x, y; int dx, dy, nextDx, nextDy;
        Entity(int cx, int cy) { x = cx * TILE; y = cy * TILE; }
    }

    private Entity pac;
    private ArrayList<Entity> ghosts = new ArrayList<>();

    public void run(String arg) {
    	 IJ.resetEscape();
        setupLevel(true);
        ColorProcessor cp = new ColorProcessor(W, H);
        ImagePlus imp = new ImagePlus("Pacman Centered", cp);
        imp.show();

        KeyAdapter ka = new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_UP || k == KeyEvent.VK_W) { up=true; down=false; left=false; right=false; }
                if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S) { up=false; down=true; left=false; right=false; }
                if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) { up=false; down=false; left=true; right=false; }
                if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) { up=false; down=false; left=false; right=true; }
            }
        };
        
        imp.getCanvas().addKeyListener(ka);
        imp.getCanvas().requestFocus();

        while (running && !IJ.escapePressed()) {
            if (imp.getWindow() == null || imp.getWindow().isClosed()) break;
            handleMovement();
            updateLogic();
            render(imp, cp);
            IJ.wait(50);
            frame++;
            if (powerTimer > 0) powerTimer--;
            if (lives <= 0) { IJ.showMessage("Game Over", "Score: " + score); setupLevel(true); }
        }
    }

    private void setupLevel(boolean reset) {
        if (reset) { score = 0; lives = 3; level = 1; }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                map[r][c] = (r == 0 || r == ROWS - 1 || c == 0 || c == COLS - 1 || (r%2==0 && c%2==0)) ? 0 : 2;
            }
        }
        pac = new Entity(10, 15);
        ghosts.clear();
        for (int i = 0; i < 3; i++) ghosts.add(new Entity(10, 9));
    }

    private void handleMovement() {
        if (up) { pac.nextDx = 0; pac.nextDy = -1; }
        if (down) { pac.nextDx = 0; pac.nextDy = 1; }
        if (left) { pac.nextDx = -1; pac.nextDy = 0; }
        if (right) { pac.nextDx = 1; pac.nextDy = 0; }
    }

    private void updateLogic() {
        if (canMove(pac.x, pac.y, pac.nextDx, pac.nextDy)) { pac.dx = pac.nextDx; pac.dy = pac.nextDy; }
        if (canMove(pac.x, pac.y, pac.dx, pac.dy)) { pac.x += pac.dx * 6; pac.y += pac.dy * 6; }

        for (Entity g : ghosts) {
            if (!canMove(g.x, g.y, g.dx, g.dy) || Math.random() < 0.05) {
                int[][] d = {{0,1}, {0,-1}, {1,0}, {-1,0}};
                int[] r = d[(int)(Math.random()*4)];
                g.dx = r[0]; g.dy = r[1];
            }
            g.x += g.dx * 4; g.y += g.dy * 4;
            if (Math.hypot(pac.x - g.x, pac.y - g.y) < TILE * 0.7) {
                lives--; pac.x = 10*TILE; pac.y = 15*TILE; IJ.wait(600);
            }
        }
    }

    private boolean canMove(double x, double y, int dx, int dy) {
        if (dx == 0 && dy == 0) return false;
        int nx = (int)(x + dx * 8 + TILE/2) / TILE;
        int ny = (int)(y + dy * 8 + TILE/2) / TILE;
        return nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS && map[ny][nx] != 0;
    }

    private void render(ImagePlus imp, ColorProcessor cp) {
        cp.setColor(Color.BLACK); cp.fill();
        Overlay ov = new Overlay();
        
        // Draw Walls
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (map[r][c] == 0) {
                    cp.setColor(new Color(0, 80, 200));
                    cp.drawRect(c*TILE+2, r*TILE+2, TILE-4, TILE-4);
                }
            }
        }

        // Draw Pacman
        OvalRoi pRoi = new OvalRoi(pac.x + 2, pac.y + 2, TILE - 4, TILE - 4);
        pRoi.setFillColor(Color.YELLOW); ov.add(pRoi);

        // Draw Centered Ghosts
        Color[] gCols = {Color.RED, Color.PINK, Color.CYAN, Color.ORANGE};
        for (int i=0; i<ghosts.size(); i++) {
            Entity g = ghosts.get(i);
            double gx = g.x + 2; double gy = g.y + 2; // Offset for centering in TILE
            int size = TILE - 4;
            
            OvalRoi body = new OvalRoi(gx, gy, size, size);
            body.setFillColor(gCols[i % 4]); ov.add(body);
            
            // Fixed Eyes (White)
            OvalRoi e1 = new OvalRoi(gx + 3 + g.dx*2, gy + 4 + g.dy*2, 5, 5); 
            e1.setFillColor(Color.WHITE); ov.add(e1);
            OvalRoi e2 = new OvalRoi(gx + 10 + g.dx*2, gy + 4 + g.dy*2, 5, 5); 
            e2.setFillColor(Color.WHITE); ov.add(e2);
            
            // Pupils (Blue)
            OvalRoi p1 = new OvalRoi(gx + 4 + g.dx*3, gy + 5 + g.dy*3, 2, 2); 
            p1.setFillColor(Color.BLUE); ov.add(p1);
            OvalRoi p2 = new OvalRoi(gx + 11 + g.dx*3, gy + 5 + g.dy*3, 2, 2); 
            p2.setFillColor(Color.BLUE); ov.add(p2);
        }

        TextRoi txt = new TextRoi(ARENA_W + 20, 30, "SCORE: " + score + "\nLIVES: " + lives);
        txt.setStrokeColor(Color.WHITE); ov.add(txt);
        imp.setOverlay(ov);
        imp.updateAndDraw();
    }
}
