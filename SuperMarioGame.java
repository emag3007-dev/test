package games.trial;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * SuperMario-like minimal demo using Swing.
 * Controls:
 *  - Left/Right arrows: move
 *  - Space / Up arrow: jump
 *  - R: restart after Game Over
 *
 * This is intentionally simple: rectangular player, platforms, coins and
 * simple enemy patrols. It's a starting point you can extend.
 */
public class SuperMarioGame extends JPanel implements ActionListener, KeyListener {
    private static final int WIDTH = 900;
    private static final int HEIGHT = 360;

    // Player
    private final Rectangle player;
    private double vx = 0;
    private double vy = 0;
    private static final double GRAVITY = 0.9;
    private static final double MOVE_SPEED = 3.5;
    private static final double JUMP_SPEED = -13;
    private boolean onGround = false;

    // --- doppio salto: contatori e flags ---
    private int jumpsUsed = 0;           // quanti salti sono stati effettuati (0 = nessuno)
    private static final int MAX_JUMPS = 2; // permette il doppio salto
    private boolean jumpKeyDown = false; // evita ripetizioni mentre il tasto è tenuto premuto

    // World
    private final ArrayList<Rectangle> platforms = new ArrayList<>();
    private final ArrayList<Rectangle> coins = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();

    // Camera offset for simple side-scroll
    private int camX = 0;

    // Loop
    private final Timer timer;
    private boolean running = true;
    private int score = 0;
    private int lives = 2; // numero di vite del personaggio
    private static final int COIN_VALUE = 150; // valore di ogni banconota raccolta
    private static final int ENEMY_VALUE = 200; // valore quando si uccide un nemico
    private boolean completed = false; // livello completato (massimo raggiungibile == 5000)
    // completion by distance
    private static final double METERS_TO_COMPLETE = 100.0; // finish at 100 meters
    private int flagPixelX = -1; // world x position where the flag will be placed

    private final Random rand = new Random();
    // distance tracking
    private int startX = 0;            // punto di partenza in pixel
    private int maxXReached = 0;       // massimo x raggiunto (per contare solo avanzamento a destra)
    private double metersTraveled = 0; // metri percorsi (calcolati ma non mostrati)
    private static final double PIXELS_PER_METER = 100.0; // assunzione: 100 px = 1 metro

    public SuperMarioGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(135, 206, 235)); // sky blue
        setFocusable(true);
        addKeyListener(this);

        // Player rect
        player = new Rectangle(80, HEIGHT - 120, 32, 48);

        buildLevel();

        timer = new Timer(16, this);
        timer.start();
    }

    private void buildLevel() {
        platforms.clear();
        coins.clear();
        enemies.clear();

        // reset completed flag
        completed = false;

        // reset flag and distance tracking
        flagPixelX = -1;
        startX = 0;
        maxXReached = 0;
        metersTraveled = 0.0;

        // Extended ground: ensure it's long enough to reach METERS_TO_COMPLETE
        int required = (int)(METERS_TO_COMPLETE * PIXELS_PER_METER) + 2000; // margin
        int levelLength = Math.max(10000, required);
        platforms.add(new Rectangle(-1000, HEIGHT - 40, levelLength, 40));

        // Procedurally generate platforms across the level with occasional gaps
        int x = 200;
        Random r = rand;
        while (x < levelLength - 200) {
            int pw = 80 + r.nextInt(80); // platform width
            int phY = HEIGHT - (40 + 60 + r.nextInt(160)); // vary platform height
            platforms.add(new Rectangle(x, phY, pw, 16));

            // Place 1-3 banknotes above the platform
            int notes = 1 + r.nextInt(3);
            for (int i = 0; i < notes; i++) {
                int bw = 30;
                int bh = 14;
                int cx = x + 6 + i * (bw + 6);
                if (cx + bw < x + pw - 6) {
                    coins.add(new Rectangle(cx, phY - bh - 6, bw, bh));
                }
            }

            // Occasionally add an enemy patrolling near this platform
            if (r.nextDouble() < 0.25) {
                int ex = x + pw/2;
                int ey = HEIGHT - 40 - 24; // ground enemy
                int ew = 24;
                int eh = 24;
                int left = Math.max(0, ex - 80);
                int right = ex + 80;
                enemies.add(new Enemy(ex, ey, ew, eh, left, right));
            }

            // advance x with random gap
            x += pw + 80 + r.nextInt(120);
        }

        // Add a few fixed enemies near the end of the level as well
        enemies.add(new Enemy(2000, HEIGHT - 40 - 24, 28, 28, 1950, 2100));
        enemies.add(new Enemy(3200, HEIGHT - 40 - 24, 28, 28, 3160, 3300));
        enemies.add(new Enemy(4800, HEIGHT - 40 - 28, 30, 30, 4750, 4900));
        enemies.add(new Enemy(6400, HEIGHT - 40 - 28, 30, 30, 6360, 6500));

        // reset camera and player
        camX = 0;
        player.x = 80;
        player.y = HEIGHT - 120;
        vx = 0;
        vy = 0;
        onGround = false;
        score = 0;
        running = true;

        // reset doppio salto
        jumpsUsed = 0;
        jumpKeyDown = false;

        // reset lives
        lives = 2;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Translate camera
        g2.translate(-camX, 0);

        // Draw background parallax (simple)
        g2.setColor(new Color(100, 155, 100));
        g2.fillRect(camX, HEIGHT - 40, WIDTH * 2, 40);

        // Platforms
        g2.setColor(new Color(100, 60, 30));
        for (Rectangle p : platforms) {
            // if this is the main ground and a flag is placed, draw left and right pieces leaving a gap
            if (flagPixelX > 0 && p.y == HEIGHT - 40 && p.width > 200) {
                int gapHalf = 12; // half width of the gap around the flag
                int gapStart = flagPixelX - gapHalf;
                int gapEnd = flagPixelX + gapHalf;
                // left piece
                int leftX = p.x;
                int leftW = Math.max(0, gapStart - p.x);
                if (leftW > 0) g2.fillRect(leftX, p.y, leftW, p.height);
                // right piece
                int rightX = Math.max(p.x, gapEnd);
                int rightW = Math.max(0, p.x + p.width - rightX);
                if (rightW > 0) g2.fillRect(rightX, p.y, rightW, p.height);
            } else {
                g2.fillRect(p.x, p.y, p.width, p.height);
            }
        }

        // Coins
        // Draw coins as euro-style banknotes (rounded rectangles with € symbol)
        for (Rectangle c : coins) {
            int bw = c.width;
            int bh = c.height;
            // outer card
            g2.setColor(new Color(200, 230, 180)); // light green-ish banknote base
            g2.fillRoundRect(c.x, c.y, bw, bh, 6, 6);
            // inner stripe
            g2.setColor(new Color(170, 210, 150));
            g2.fillRoundRect(c.x + 3, c.y + 3, bw - 6, bh - 6, 4, 4);
            // small emblem circle on left
            g2.setColor(new Color(120, 150, 100));
            g2.fillOval(c.x + 3, c.y + bh/2 - 6, 12, 12);
            // decorative horizontal lines (print details)
            g2.setColor(new Color(120, 140, 110));
            g2.setStroke(new BasicStroke(1f));
            int startX = c.x + bw/2 - 8;
            for (int i = 0; i < 3; i++) {
                int ly = c.y + 3 + i * 4;
                g2.drawLine(startX, ly, startX + 12, ly);
            }
            // euro symbol on right
            g2.setColor(new Color(20, 60, 20));
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, bh - 2)));
            FontMetrics fm = g2.getFontMetrics();
            String euro = "€";
            int ex = c.x + bw - fm.stringWidth(euro) - 4;
            int ey = c.y + (bh + fm.getAscent())/2 - 2;
            g2.drawString(euro, ex, ey);
        }

        // Enemies
        // Enemies (draw Goomba-like sprites)
        for (Enemy en : enemies) {
            int ex = en.r.x;
            int ey = en.r.y;
            int ew = en.r.width;
            int eh = en.r.height;

            // shadow under enemy
            g2.setColor(new Color(30, 30, 30, 100));
            g2.fillOval(ex + ew/4, ey + eh - 4, ew/2, 6);

            // body (rounded)
            g2.setColor(new Color(150, 75, 30)); // brown
            g2.fillRoundRect(ex, ey, ew, eh, ew/2, eh/2);

            // darker top (cap)
            g2.setColor(new Color(120, 60, 20));
            g2.fillArc(ex, ey - eh/6, ew, eh/2, 0, 180);

            // eyes
            int eyeW = Math.max(3, ew/5);
            int eyeH = Math.max(3, eh/5);
            int leftEyeX = ex + ew/4 - eyeW/2;
            int rightEyeX = ex + 3*ew/4 - eyeW/2 - 2;
            int eyeY = ey + eh/4;
            g2.setColor(Color.white);
            g2.fillOval(leftEyeX, eyeY, eyeW, eyeH);
            g2.fillOval(rightEyeX, eyeY, eyeW, eyeH);
            g2.setColor(Color.black);
            g2.fillOval(leftEyeX + eyeW/3, eyeY + eyeH/4, Math.max(2, eyeW/2), Math.max(2, eyeH/2));
            g2.fillOval(rightEyeX + eyeW/3, eyeY + eyeH/4, Math.max(2, eyeW/2), Math.max(2, eyeH/2));

            // angry eyebrows
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(70, 35, 10));
            g2.drawLine(leftEyeX, eyeY - 2, leftEyeX + eyeW, eyeY - 4);
            g2.drawLine(rightEyeX + eyeW, eyeY - 2, rightEyeX, eyeY - 4);

            // feet
            g2.setColor(new Color(60, 30, 10));
            int footW = Math.max(6, ew/3);
            int footH = Math.max(4, eh/6);
            g2.fillRect(ex + 4, ey + eh - footH/2, footW, footH);
            g2.fillRect(ex + ew - footW - 4, ey + eh - footH/2, footW, footH);
        }

        // Draw completion flag in world coordinates if placed
        if (flagPixelX >= 0) {
            int poleHeight = 40;
            int poleWidth = 4;
            int poleX = flagPixelX;
            int poleY = HEIGHT - 40 - poleHeight; // ground Y is HEIGHT - 40
            // pole
            g2.setColor(new Color(100, 60, 20));
            g2.fillRect(poleX - poleWidth/2, poleY, poleWidth, poleHeight + 6);
            // flag (triangle)
            int flagW = 20;
            int flagH = 12;
            Polygon flagPoly = new Polygon();
            flagPoly.addPoint(poleX + 1, poleY + 6);
            flagPoly.addPoint(poleX + 1 + flagW, poleY + 6 + flagH/2);
            flagPoly.addPoint(poleX + 1, poleY + 6 + flagH);
            g2.setColor(new Color(200, 20, 20));
            g2.fillPolygon(flagPoly);
            // stacked base to look 'impilata' in the terrain
            g2.setColor(new Color(80, 50, 30));
            g2.fillRect(poleX - 6, HEIGHT - 40, 12, 6);
            g2.fillRect(poleX - 8, HEIGHT - 34, 16, 4);
        }

        // Player (draw a woman with curly brown hair and round black glasses)
        int px = player.x;
        int py = player.y;
        int pw = player.width;
        int ph = player.height;

        // Hair: draw multiple dark-brown ovals around the head to suggest curls
        g2.setColor(new Color(95, 57, 19)); // dark brown
        int hairTop = py - 6;
        int hairLeft = px - 4;
        int hairRight = px + pw - 4;
        // top curls
        g2.fillOval(hairLeft, hairTop, pw/2, ph/3);
        g2.fillOval(px + pw/4, hairTop - 4, pw/2 + 4, ph/3 + 4);
        g2.fillOval(hairRight - pw/2, hairTop, pw/2, ph/3);
        // side curls
        g2.fillOval(px - 6, py + 2, 10, 10);
        g2.fillOval(px + pw - 4, py + 2, 10, 10);
        // back hair under chin area
        g2.fillOval(px - 2, py + ph/4, pw + 4, ph/2);

        // Face (skin tone)
        g2.setColor(new Color(255, 220, 180));
        g2.fillOval(px + 4, py + 4, pw - 8, ph/2 - 2);

        // Glasses: round black frames
        int glassSize = 10;
        int gx = px + pw/2 - glassSize - 2;
        int gy = py + ph/6 + 2;
        g2.setColor(Color.black);
        g2.fillOval(gx, gy, glassSize, glassSize);
        g2.fillOval(gx + glassSize + 4, gy, glassSize, glassSize);
        // bridge
        g2.fillRect(gx + glassSize, gy + glassSize/2 - 1, 4, 2);
        // lenses (slightly lighter inside to simulate glass)
        g2.setColor(new Color(230, 230, 240, 200));
        g2.fillOval(gx + 2, gy + 2, glassSize - 4, glassSize - 4);
        g2.fillOval(gx + glassSize + 6, gy + 2, glassSize - 4, glassSize - 4);

        // Eyes behind glasses (small dark pupils)
        g2.setColor(new Color(30, 30, 30));
        g2.fillOval(gx + 3, gy + 3, 3, 3);
        g2.fillOval(gx + glassSize + 7, gy + 3, 3, 3);

        // Mouth
        g2.setColor(new Color(160, 40, 60));
        g2.fillOval(px + pw/2 - 6, py + ph/3 + 8, 12, 6);

        // Shirt/top (soft color)
        g2.setColor(new Color(200, 100, 140)); // muted pink/mauve
        g2.fillRect(px + 2, py + ph/2 - 2, pw - 4, ph/3);

        // Arms (simple)
        g2.setColor(new Color(255, 220, 180));
        g2.fillRect(px - 4, py + ph/2, 6, ph/6);
        g2.fillRect(px + pw - 2, py + ph/2, 6, ph/6);

        // Skirt/legs
        g2.setColor(new Color(120, 70, 150));
        g2.fillRect(px + 4, py + ph/2 + ph/6, pw - 8, ph/6);

        // Shoes
        g2.setColor(new Color(60, 40, 30));
        g2.fillRect(px + 6, py + ph - 8, 8, 6);
        g2.fillRect(px + pw - 14, py + ph - 8, 8, 6);

        // Undo camera translation so HUD is fixed to the window (top-left)
        g2.translate(camX, 0);
        g2.setColor(Color.black);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("Score: " + score, 10, 24);
        g2.drawString("Lives: " + lives, 10, 44);

        if (!running) {
            if (completed) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                String s = "completed";
                int sw = g2.getFontMetrics().stringWidth(s);
                g2.drawString(s, (WIDTH - sw) / 2, HEIGHT / 2 - 10);
                return;
            }
            g2.setFont(new Font("SansSerif", Font.BOLD, 36));
            String s = "Game Over";
            int sw = g2.getFontMetrics().stringWidth(s);
            g2.drawString(s, (WIDTH - sw) / 2, HEIGHT / 2 - 10);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            String t = "Press R to restart";
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (WIDTH - tw) / 2, HEIGHT / 2 + 20);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!running) {
            repaint();
            return;
        }

         // salvo la Y precedente del giocatore per rilevare il tipo di collisione con i nemici
         int prevPlayerY = player.y;

         // Apply gravity
         vy += GRAVITY;
         player.y += (int) Math.round(vy);

         // Horizontal movement integration
         player.x += (int) Math.round(vx);

        // Initialize startX on first movement (use player's initial x as origin)
        if (startX == 0) {
            startX = player.x;
            maxXReached = player.x;
        }

        // Update maxXReached and compute meters traveled (only forward progress counts)
        if (player.x > maxXReached) {
            maxXReached = player.x;
            metersTraveled = (maxXReached - startX) / PIXELS_PER_METER;
            // non mostrare metri, ma completare il gioco quando raggiunge la soglia in metri
            if (metersTraveled >= METERS_TO_COMPLETE) {
                completed = true;
                running = false;
                // place flag at exact completion pixel (world coord) but clamp inside main ground
                int desiredFlagX = startX + (int) (METERS_TO_COMPLETE * PIXELS_PER_METER);
                // find main ground platform (y == HEIGHT - 40 and large width)
                int groundStart = Integer.MIN_VALUE;
                int groundEnd = Integer.MIN_VALUE;
                for (Rectangle gp : platforms) {
                    if (gp.y == HEIGHT - 40 && gp.width > 200) {
                        groundStart = gp.x;
                        groundEnd = gp.x + gp.width;
                        break;
                    }
                }
                if (groundStart != Integer.MIN_VALUE) {
                    int margin = 20;
                    flagPixelX = Math.max(groundStart + margin, Math.min(desiredFlagX, groundEnd - margin));
                } else {
                    flagPixelX = desiredFlagX;
                }
                // Physically split the main ground platform into two pieces so the gap becomes collidable
                for (int i = 0; i < platforms.size(); i++) {
                    Rectangle gp = platforms.get(i);
                    if (gp.y == HEIGHT - 40 && gp.width > 200) {
                        int gapHalf = 12;
                        int gapStart = flagPixelX - gapHalf;
                        int gapEnd = flagPixelX + gapHalf;
                        int leftX = gp.x;
                        int leftW = Math.max(0, gapStart - gp.x);
                        int rightX = Math.max(gp.x, gapEnd);
                        int rightW = Math.max(0, gp.x + gp.width - rightX);

                        // remove original ground and insert left and right pieces (if > 0 width)
                        platforms.remove(i);
                        int insertIndex = i;
                        if (leftW > 0) {
                            platforms.add(insertIndex, new Rectangle(leftX, gp.y, leftW, gp.height));
                            insertIndex++;
                        }
                        if (rightW > 0) {
                            platforms.add(insertIndex, new Rectangle(rightX, gp.y, rightW, gp.height));
                        }
                        break;
                    }
                }
                 timer.stop();
                 repaint();
                 return;
             }
         }

         // Simple collision with platforms (vertical)
         onGround = false;
         for (Rectangle p : platforms) {
             if (player.intersects(p)) {
                 Rectangle inter = player.intersection(p);
                 if (inter.height < inter.width) {
                     // vertical collision
                     if (player.y < p.y) {
                         // landed on top
                         player.y = p.y - player.height;
                         vy = 0;
                         onGround = true;

                         // quando atterra, azzeriamo il conteggio dei salti per permettere il doppio salto di nuovo
                         jumpsUsed = 0;
                     } else {
                         // hit from below
                         player.y = p.y + p.height;
                         vy = 0;
                     }
                 } else {
                     // horizontal collision: push back
                     if (player.x < p.x) {
                         player.x = p.x - player.width;
                     } else {
                         player.x = p.x + p.width;
                     }
                     vx = 0;
                 }
             }
         }

         // Coins collection
         Iterator<Rectangle> cit = coins.iterator();
         while (cit.hasNext()) {
             Rectangle c = cit.next();
             if (player.intersects(c)) {
                 cit.remove();
                 score += COIN_VALUE;
             }
         }

        // dopo la raccolta, controlla se il massimo punteggio raggiungibile è 5000
        int maxAchievable = score + coins.size() * COIN_VALUE + enemies.size() * ENEMY_VALUE;
        if (maxAchievable == 5000) {
            // fermiamo il gioco e segnaliamo il completamento
            completed = true;
            running = false;
            timer.stop();
            repaint();
            return;
        }

         // Update enemies: permettiamo di "stomp" i nemici se il giocatore li colpisce dall'alto mentre sta cadendo
         Iterator<Enemy> eit = enemies.iterator();
         while (eit.hasNext()) {
             Enemy en = eit.next();
             en.update();
             if (player.intersects(en.r)) {
                 int playerBottomPrev = prevPlayerY + player.height;
                 int enemyTop = en.r.y;
                 // condizione di stomp: il giocatore stava cadendo (vy > 0) e la sua parte inferiore nel frame precedente
                 // era sopra l'inizio del nemico (quindi ha colpito dall'alto)
                 if (vy > 0 && playerBottomPrev <= enemyTop + 6) {
                     // uccidi il nemico
                     eit.remove();
                     // piccolo rimbalzo
                     vy = JUMP_SPEED / 1.6;
                     score += 200;
                 } else {
                     // altrimenti il giocatore perde una vita
                     lives--;
                     if (lives > 0) {
                         // respawn semplice: riporta il giocatore al punto di partenza
                         player.x = 80;
                         player.y = HEIGHT - 120;
                         vx = 0;
                         vy = 0;
                         jumpsUsed = 0;
                         // sposta la camera all'inizio
                         camX = 0;
                         // rimuoviamo eventuali collisioni immediate con lo stesso nemico muovendolo indietro
                         en.r.x = en.rightBound; // sposta il nemico al limite destro per evitare ricolisone istantanea
                     } else {
                         running = false;
                     }
                 }
             }
         }

        // dopo la gestione nemici, ricontrolla il massimo raggiungibile (nel caso lo stomp o la morte abbiano cambiato la situazione)
        maxAchievable = score + coins.size() * COIN_VALUE + enemies.size() * ENEMY_VALUE;
        if (maxAchievable == 5000) {
            completed = true;
            running = false;
            timer.stop();
            repaint();
            return;
        }

         // Camera follows player, keep player more to the left area
         int targetCamX = Math.max(0, player.x - 150);
         // Smooth camera
         camX += (targetCamX - camX) / 8;

        repaint();
    }

    private void restart() {
        buildLevel();
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    @Override
    public void keyPressed(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc == KeyEvent.VK_LEFT) {
            vx = -MOVE_SPEED;
        } else if (kc == KeyEvent.VK_RIGHT) {
            vx = MOVE_SPEED;
        } else if (kc == KeyEvent.VK_SPACE || kc == KeyEvent.VK_UP) {
            // doppio salto: permetti fino a MAX_JUMPS salti, evita di ripetere mentre il tasto è mantenuto
            if (!jumpKeyDown && running && jumpsUsed < MAX_JUMPS) {
                vy = JUMP_SPEED;
                jumpsUsed++;
                onGround = false;
                jumpKeyDown = true;
            }
        } else if (kc == KeyEvent.VK_R) {
            if (!running) restart();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc == KeyEvent.VK_LEFT || kc == KeyEvent.VK_RIGHT) {
            vx = 0;
        } else if (kc == KeyEvent.VK_SPACE || kc == KeyEvent.VK_UP) {
            // rilasciando il tasto jump permettiamo la nuova pressione
            jumpKeyDown = false;
        }
    }

    private static class Enemy {
        Rectangle r;
        int leftBound, rightBound;
        int dir = 1;
        int speed = 2;

        Enemy(int x, int y, int w, int h, int left, int right) {
            r = new Rectangle(x, y, w, h);
            leftBound = left;
            rightBound = right;
        }

        void update() {
            r.x += dir * speed;
            if (r.x < leftBound) {
                r.x = leftBound;
                dir = 1;
            } else if (r.x > rightBound) {
                r.x = rightBound;
                dir = -1;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Super Mario - simple demo");
            SuperMarioGame game = new SuperMarioGame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.getContentPane().add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
