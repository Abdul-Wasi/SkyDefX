// SatelliteMapManager.java
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

public class SatelliteMapManager {

    private double centerLat = 40.785091; // Default: Central Park, NY
    private double centerLon = -73.968285;
    private int zoom = 13; // Default zoom level

    private final Map<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final Set<String> activeDownloads = ConcurrentHashMap.newKeySet();
    private final File cacheDir;
    private final ExecutorService downloadExecutor;
    private final Runnable onMapUpdated;

    // Default placeholder tile (dark texture grid)
    private BufferedImage placeholderTile;

    public SatelliteMapManager(Runnable onMapUpdated) {
        this.onMapUpdated = onMapUpdated;
        this.downloadExecutor = Executors.newFixedThreadPool(4);

        // Define a local cache folder inside the workspace
        this.cacheDir = new File(".cache/map_tiles");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        createPlaceholderTile();
    }

    private void createPlaceholderTile() {
        placeholderTile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = placeholderTile.createGraphics();
        g.setColor(new Color(15, 20, 15)); // Dark background
        g.fillRect(0, 0, 256, 256);
        g.setColor(new Color(0, 45, 0)); // Very dark green gridlines
        g.drawRect(0, 0, 255, 255);
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(0, 80, 0));
        g.drawString("LOADING SATELLITE TILE...", 30, 128);
        g.dispose();
    }

    public synchronized void setCenter(double lat, double lon) {
        // Clamp latitude to Mercator limits
        this.centerLat = Math.max(-85.0, Math.min(85.0, lat));
        this.centerLon = Math.max(-180.0, Math.min(180.0, lon));
        System.out.println("SatelliteMapManager: Coordinates updated to " + centerLat + ", " + centerLon);
        triggerMapRepaint();
    }

    public synchronized void setZoom(int newZoom) {
        // Clamp zoom level between 1 and 19 (typical Esri World Imagery support)
        this.zoom = Math.max(1, Math.min(19, newZoom));
        System.out.println("SatelliteMapManager: Zoom level updated to " + zoom);
        triggerMapRepaint();
    }

    public double getCenterLat() { return centerLat; }
    public double getCenterLon() { return centerLon; }
    public int getZoom() { return zoom; }

    /**
     * Calculates the ground resolution (meters per pixel) at the current latitude and zoom.
     */
    public double getMetersPerPixel() {
        double latRad = Math.toRadians(centerLat);
        return (40075016.686 * Math.cos(latRad)) / (256.0 * (1 << zoom));
    }

    public void drawMap(Graphics2D g2d, int panelWidth, int panelHeight) {
        double latRad = Math.toRadians(centerLat);

        // Web Mercator tile projection
        double tileX = (centerLon + 180.0) / 360.0 * (1 << zoom);
        double tileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom);

        int centerTileX = (int) Math.floor(tileX);
        int centerTileY = (int) Math.floor(tileY);

        double offsetX = (tileX - centerTileX) * 256.0;
        double offsetY = (tileY - centerTileY) * 256.0;

        // Coordinates of the top-left corner of the center tile relative to the panel center
        int drawStartX = panelWidth / 2 - (int) Math.round(offsetX);
        int drawStartY = panelHeight / 2 - (int) Math.round(offsetY);

        // Determine how many tiles we need to draw to cover the panel.
        // Screen width ~ 1200 (need ~5 tiles horizontally), height ~ 800 (need ~4 tiles vertically)
        int tileRangeX = (panelWidth / 512) + 2;
        int tileRangeY = (panelHeight / 512) + 2;

        for (int dx = -tileRangeX; dx <= tileRangeX; dx++) {
            for (int dy = -tileRangeY; dy <= tileRangeY; dy++) {
                int tx = centerTileX + dx;
                int ty = centerTileY + dy;

                // Handle out of bound tiles (wrap longitude, clamp latitude)
                int maxTiles = 1 << zoom;
                if (ty < 0 || ty >= maxTiles) {
                    continue; // Latitude bounds
                }
                tx = (tx % maxTiles + maxTiles) % maxTiles; // Longitude wrap

                BufferedImage tileImg = getTile(zoom, tx, ty);

                int posX = drawStartX + dx * 256;
                int posY = drawStartY + dy * 256;

                if (tileImg != null) {
                    g2d.drawImage(tileImg, posX, posY, null);
                } else {
                    g2d.drawImage(placeholderTile, posX, posY, null);
                }
            }
        }
    }

    private BufferedImage getTile(int z, int x, int y) {
        String key = z + "_" + x + "_" + y;
        BufferedImage img = tileCache.get(key);
        if (img != null) {
            return img;
        }

        // Check disk cache asynchronously to not block paint loops, or load synchronously if it is fast.
        // Here we do a fast synchronous check on the disk. File operations on SSD are fast enough.
        File cachedFile = new File(cacheDir, key + ".jpg");
        if (cachedFile.exists()) {
            try {
                BufferedImage diskImg = ImageIO.read(cachedFile);
                if (diskImg != null) {
                    tileCache.put(key, diskImg);
                    return diskImg;
                }
            } catch (IOException e) {
                System.err.println("SatelliteMapManager: Failed to read disk cache for " + key + ": " + e.getMessage());
            }
        }

        // If not cached, queue download in background
        queueTileDownload(z, x, y, cachedFile, key);
        return null; // Return null so placeholder draws while loading
    }

    private void queueTileDownload(int z, int x, int y, File destFile, String key) {
        if (activeDownloads.contains(key)) {
            return; // Already downloading
        }

        activeDownloads.add(key);
        downloadExecutor.submit(() -> {
            try {
                String urlStr = String.format("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d", z, y, x);
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         OutputStream out = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    // Load the newly downloaded tile
                    BufferedImage loadedImg = ImageIO.read(destFile);
                    if (loadedImg != null) {
                        tileCache.put(key, loadedImg);
                        triggerMapRepaint();
                    }
                } else {
                    System.err.println("SatelliteMapManager: Server returned code " + conn.getResponseCode() + " for tile " + key);
                }
            } catch (Exception e) {
                System.err.println("SatelliteMapManager: Failed to download tile " + key + ": " + e.getMessage());
            } finally {
                activeDownloads.remove(key);
            }
        });
    }

    private void triggerMapRepaint() {
        if (onMapUpdated != null) {
            onMapUpdated.run();
        }
    }

    public void shutdown() {
        downloadExecutor.shutdownNow();
    }
}
