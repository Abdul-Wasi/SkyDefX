// VideoFeedPanel.java
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoFeedPanel extends JPanel {
    private BufferedImage currentFrame;
    private String statusMessage = "STANDBY: WAITING FOR VIDEO STREAM...";

    public VideoFeedPanel() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(400, 300));
        
        // Military style titled border
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(0, 100, 0), 1),
            " OPTICAL TARGET ACQUISITION (LIVE) ",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("Monospaced", Font.BOLD, 11),
            new Color(50, 255, 50)
        ));
    }

    public synchronized void updateFrame(BufferedImage img) {
        this.currentFrame = img;
        this.statusMessage = null;
        repaint();
    }

    public synchronized void setStatus(String msg) {
        this.statusMessage = msg;
        this.currentFrame = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        synchronized (this) {
            if (currentFrame != null) {
                int imgW = currentFrame.getWidth();
                int imgH = currentFrame.getHeight();
                
                // Calculate scale maintaining aspect ratio
                double scale = Math.min((double) (w - 20) / imgW, (double) (h - 30) / imgH);
                int drawW = (int) (imgW * scale);
                int drawH = (int) (imgH * scale);
                int x = (w - drawW) / 2;
                int y = (h - drawH) / 2 + 5; // Offset slightly for border title

                // Draw the video frame
                g2.drawImage(currentFrame, x, y, drawW, drawH, null);

                // Draw Military HUD crosshairs (centered relative to image display bounds)
                g2.setColor(new Color(0, 255, 0, 120));
                g2.setStroke(new BasicStroke(1.2f));
                
                int cx = w / 2;
                int cy = h / 2 + 5;
                
                // Center reticle
                g2.drawLine(cx - 15, cy, cx + 15, cy);
                g2.drawLine(cx, cy - 15, cx, cy + 15);
                g2.drawOval(cx - 25, cy - 25, 50, 50);
                
                // Corner brackets
                int pad = 12;
                int offset = 10;
                g2.drawPolyline(new int[]{pad + offset, pad, pad, pad}, new int[]{pad + 10, pad + 10, pad + 10, pad + 20}, 4); // Top-left
                g2.drawPolyline(new int[]{w - pad - offset, w - pad, w - pad, w - pad}, new int[]{pad + 10, pad + 10, pad + 10, pad + 20}, 4); // Top-right
                g2.drawPolyline(new int[]{pad + offset, pad, pad, pad}, new int[]{h - pad, h - pad, h - pad, h - pad - 10}, 4); // Bottom-left
                g2.drawPolyline(new int[]{w - pad - offset, w - pad, w - pad, w - pad}, new int[]{h - pad, h - pad, h - pad, h - pad - 10}, 4); // Bottom-right
                
                // "REC" Indicator
                g2.setColor(Color.RED);
                g2.fillOval(pad + 15, pad + 20, 8, 8);
                g2.setColor(new Color(50, 255, 50));
                g2.setFont(new Font("Monospaced", Font.BOLD, 9));
                g2.drawString("REC LIVE", pad + 27, pad + 27);

            } else {
                // Standby screen
                g2.setColor(new Color(5, 10, 5));
                g2.fillRect(6, 15, w - 12, h - 21);

                // Tactical grid lines
                g2.setColor(new Color(0, 100, 0, 30));
                for (int i = 20; i < w - 20; i += 30) g2.drawLine(i, 20, i, h - 20);
                for (int i = 20; i < h - 20; i += 30) g2.drawLine(20, i, w - 20, i);

                // Standby status text
                g2.setColor(new Color(0, 200, 0));
                g2.setFont(new Font("Monospaced", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth(statusMessage)) / 2;
                int y = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(statusMessage, x, y);
            }
        }
    }
}
