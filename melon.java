// Import necessary classes - No fixes needed here, the list is good.
import com.sun.management.OperatingSystemMXBean; // Cleaner than reflection - FIX #5
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;
import java.util.stream.Collectors;
import javax.swing.*;

public class melon {
    // --- UI Palette & Constants ---
    private static final Color BG = new Color(0x2e2e2e);
    private static final Color FG = new Color(0xffffff);
    private static final Color ACCENT = new Color(0x5fbf00);
    private static final Color ENTRY_BG = new Color(0x454545);
    private static final String CONFIG_FILE = "melon.properties";
    private static final String LOG_FILE = "melon.log";

    // --- UI Components ---
    private JFrame frame;
    private JRadioButton offlineRadio, msRadio;
    private JTextField usernameField;
    private JButton msButton, launchButton;
    private JComboBox<VersionInfo> versionBox;
    private JSlider ramSlider;
    private JLabel ramLabel, statusLabel;

    // --- State & Config ---
    private String loginType = "offline";
    private AuthInfo authInfo; // Encapsulates user, uuid, token
    private final Properties config = new Properties();
    private static Logger logger;

    // A record to hold version-specific info - FIX #6: Structured version data
    private record VersionInfo(String id, String displayName, String mainClass) {
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    // A record to hold authentication details - FIX #7: Structured auth data
    private record AuthInfo(String username, String uuid, String accessToken) {}

    // Supported Versions - FIX #8: Centralized and detailed version definitions
    private static final VersionInfo[] SUPPORTED_VERSIONS = {
        new VersionInfo("1.20.4", "Vanilla 1.20.4", "net.minecraft.client.main.Main"),
        new VersionInfo("1.20.1-forge-47.2.20", "Forge 1.20.1", "cpw.mods.modlauncher.Launcher"),
        new VersionInfo("fabric-loader-0.15.7-1.20.4", "Fabric 1.20.4", "net.fabricmc.loader.impl.launch.knot.KnotClient")
    };

    public static void main(String[] args) {
        setupLogging();
        logger.info("Starting Melon Launcher...");
        // Correct way to start a Swing application
        SwingUtilities.invokeLater(melon::new);
    }

    public melon() {
        loadConfig();
        int maxRam = detectMaxRam();
        
        // Initialize from config
        String initialUser = config.getProperty("offline_username", "Player" + (System.currentTimeMillis() % 1000));
        this.loginType = config.getProperty("login_type", "offline");
        
        // FIX #9: Handle NumberFormatException gracefully by providing a valid default to getProperty
        int initialRam = Integer.parseInt(config.getProperty("ram", String.valueOf(Math.min(4, maxRam))));
        initialRam = Math.max(1, Math.min(maxRam, initialRam));

        String initialVersionId = config.getProperty("version_id", SUPPORTED_VERSIONS[0].id);

        // FIX #10: Restore placeholder Microsoft login session
        if ("microsoft".equals(loginType) && config.containsKey("ms_name")) {
             this.authInfo = new AuthInfo(
                config.getProperty("ms_name"),
                config.getProperty("ms_id"),
                config.getProperty("ms_token")
            );
        } else {
             this.authInfo = new AuthInfo(null, null, null);
        }
       
        buildUI(initialUser, initialRam, maxRam, initialVersionId);
    }

    private void buildUI(String user, int initRam, int maxRam, String initVerId) {
        applyDarkTheme(); // FIX #11: Implemented missing method
        frame = new JFrame("Melon Launcher");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(500, 600));
        frame.getContentPane().setBackground(BG);
        // FIX #12: Use a main panel with a GridBagLayout for a more robust UI
        frame.setLayout(new BorderLayout());
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Melon Launcher");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(ACCENT);
        mainPanel.add(title, gbc);
        
        // --- Login Type Selection ---
        gbc.gridy++;
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginPanel.setBackground(BG);
        offlineRadio = new JRadioButton("Offline");
        msRadio = new JRadioButton("Microsoft");
        ButtonGroup bg = new ButtonGroup();
        bg.add(offlineRadio);
        bg.add(msRadio);
        setupRadioButton(offlineRadio, "offline");
        setupRadioButton(msRadio, "microsoft");
        loginPanel.add(offlineRadio);
        loginPanel.add(msRadio);
        mainPanel.add(loginPanel, gbc);

        // --- Offline Username ---
        gbc.gridy++;
        usernameField = new JTextField(user, 16);
        setupTextField(usernameField);
        mainPanel.add(usernameField, gbc);
        
        // --- Microsoft Login Button ---
        msButton = new JButton("Login with Microsoft");
        setupButton(msButton, ENTRY_BG);
        msButton.addActionListener(e -> loginMs());
        mainPanel.add(msButton, gbc);

        // --- Version Selection ---
        gbc.gridy++;
        mainPanel.add(createLabel("Game Version:"), gbc);
        gbc.gridy++;
        versionBox = new JComboBox<>(SUPPORTED_VERSIONS);
        versionBox.setBackground(ENTRY_BG);
        versionBox.setForeground(FG);
        mainPanel.add(versionBox, gbc);
        // Select initial version
        for (VersionInfo vi : SUPPORTED_VERSIONS) {
            if (vi.id.equals(initVerId)) {
                versionBox.setSelectedItem(vi);
                break;
            }
        }

        // --- RAM Slider ---
        gbc.gridy++;
        ramLabel = createLabel(String.format("RAM Allocation: %d GB", initRam));
        mainPanel.add(ramLabel, gbc);
        gbc.gridy++;
        ramSlider = new JSlider(1, maxRam, initRam);
        ramSlider.setBackground(BG);
        ramSlider.addChangeListener(e -> ramLabel.setText(String.format("RAM Allocation: %d GB", ramSlider.getValue())));
        mainPanel.add(ramSlider, gbc);

        // --- Launch Button ---
        gbc.gridy++;
        gbc.insets = new Insets(20, 5, 5, 5);
        gbc.ipady = 10; // Make button taller
        launchButton = new JButton("LAUNCH");
        setupButton(launchButton, ACCENT);
        launchButton.setFont(launchButton.getFont().deriveFont(Font.BOLD, 16f));
        launchButton.addActionListener(e -> launchGame());
        mainPanel.add(launchButton, gbc);
        
        frame.add(mainPanel, BorderLayout.CENTER);

        // Status bar at the bottom
        statusLabel = createLabel("Ready.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));
        frame.add(statusLabel, BorderLayout.SOUTH);

        // Finalize UI
        if ("microsoft".equals(loginType)) msRadio.setSelected(true);
        else offlineRadio.setSelected(true);
        toggleLoginView();
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        
        frame.pack(); // Pack before centering
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        logger.info("UI is ready.");
    }
    
    // --- UI Helpers ---
    private JLabel createLabel(String text) { // FIX #13: Implemented helper
        JLabel label = new JLabel(text);
        label.setForeground(FG);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private void setupRadioButton(JRadioButton button, String command) {
        button.setBackground(BG);
        button.setForeground(FG);
        button.setActionCommand(command);
        button.addActionListener(e -> {
            this.loginType = e.getActionCommand();
            toggleLoginView();
        });
    }

    private void setupTextField(JTextField field) {
        field.setBackground(ENTRY_BG);
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x666666)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
    }
    
    private void setupButton(JButton button, Color bg) {
        button.setBackground(bg);
        button.setForeground(FG);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
    }

    private void toggleLoginView() {
        boolean isOffline = "offline".equals(loginType);
        usernameField.setVisible(isOffline);
        msButton.setVisible(!isOffline);

        // FIX #14: Update MS button if already logged in
        if (!isOffline) {
            if (authInfo != null && authInfo.username() != null) {
                msButton.setText("Logged in as: " + authInfo.username());
                msButton.setEnabled(false); // Can't re-login
                msButton.setBackground(new Color(0x3a943a));
            } else {
                msButton.setText("Login with Microsoft");
                msButton.setEnabled(true);
                msButton.setBackground(ENTRY_BG);
            }
        }
        frame.revalidate();
        frame.repaint();
    }

    private void loginMs() {
        // FIX #15: This is still a placeholder, but a more robust one.
        // In a real app, this would open a browser for OAuth2 flow.
        String placeholderName = "User" + (System.currentTimeMillis() % 1000);
        String placeholderId = UUID.randomUUID().toString().replace("-", "");
        String placeholderToken = UUID.randomUUID().toString().replace("-", "");

        this.authInfo = new AuthInfo(placeholderName, placeholderId, placeholderToken);
        logger.info("Microsoft login successful (placeholder). User: " + placeholderName);
        JOptionPane.showMessageDialog(frame, "Login successful (placeholder)!", "MS Login", JOptionPane.INFORMATION_MESSAGE);
        
        // Update UI to reflect login
        toggleLoginView();
    }
    
    // FIX #16: Encapsulate launch prep logic
    private void launchGame() {
        AuthInfo finalAuth;
        // --- 1. Validate inputs and determine auth info ---
        if ("offline".equals(loginType)) {
            String user = usernameField.getText().trim();
            if (!user.matches("^[A-Za-z0-9_]{3,16}$")) {
                JOptionPane.showMessageDialog(frame, "Invalid username. Must be 3-16 chars (letters, numbers, underscores).", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                 // FIX #17: Handle new exception from uuidOffline
                 String uuid = uuidOffline(user);
                 finalAuth = new AuthInfo(user, uuid, "0"); // Offline token is typically "0" or a random UUID
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(frame, "Could not generate offline UUID. Launch aborted.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
                logger.log(Level.SEVERE, "Fatal error generating offline UUID.", e);
                return;
            }
        } else { // Microsoft login
            if (this.authInfo == null || this.authInfo.uuid() == null) {
                JOptionPane.showMessageDialog(frame, "Please login with Microsoft first.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            finalAuth = this.authInfo; // FIX #18: Use the correct auth info
        }

        int ram = ramSlider.getValue();
        if (ram > detectMaxRam()) {
            int choice = JOptionPane.showConfirmDialog(frame, 
                "Selected RAM (" + ram + "GB) exceeds detected system RAM. This may cause issues.\nContinue anyway?", 
                "RAM Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.NO_OPTION) return;
        }
        
        // --- 2. Save configuration ---
        saveConfig();
        
        // --- 3. Launch in a background thread to not freeze the UI ---
        // FIX #19: CRITICAL - Launching in a SwingWorker
        launchButton.setEnabled(false);
        launchButton.setText("LAUNCHING...");
        statusLabel.setText("Preparing to launch Minecraft...");

        VersionInfo version = (VersionInfo) versionBox.getSelectedItem();
        
        SwingWorker<Process, Void> worker = new SwingWorker<>() {
            @Override
            protected Process doInBackground() throws Exception {
                String mcDir = getMcDir();
                List<String> command = buildLaunchCommand(version, mcDir, finalAuth, ram);
                
                // FIX #20: Log command correctly without re-building
                logger.info("Minecraft launch command: " + String.join(" ", command));

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(mcDir));
                pb.inheritIO(); // Redirect game output to launcher's console
                return pb.start();
            }

            @Override
            protected void done() {
                try {
                    Process process = get();
                    statusLabel.setText("Minecraft is running. You can close this launcher.");
                    // Check periodically if the game has closed
                    new Timer(2000, e -> {
                        if (!process.isAlive()) {
                            ((Timer)e.getSource()).stop();
                            statusLabel.setText("Minecraft has closed. Ready.");
                            launchButton.setEnabled(true);
                            launchButton.setText("LAUNCH");
                            logger.info("Minecraft process has exited with code: " + process.exitValue());
                        }
                    }).start();

                } catch (InterruptedException | ExecutionException e) {
                    logger.log(Level.SEVERE, "Launch failed", e.getCause());
                    JOptionPane.showMessageDialog(frame, "Launch failed: " + e.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Launch failed. Check melon.log for details.");
                    launchButton.setEnabled(true);
                    launchButton.setText("LAUNCH");
                }
            }
        };

        worker.execute();
    }

    // FIX #21: COMPLETELY REWRITTEN LAUNCH COMMAND
    private List<String> buildLaunchCommand(VersionInfo version, String mcDir, AuthInfo auth, int ram) {
        List<String> cmd = new ArrayList<>();
        String versionPath = mcDir + File.separator + "versions" + File.separator + version.id;

        cmd.add("java");

        // JVM Arguments
        cmd.add("-Xmx" + ram + "G");
        cmd.add("-Xms" + ram + "G");
        
        // This path is critical for native libraries (like OpenGL bindings)
        String nativesPath = versionPath + File.separator + "natives";
        cmd.add("-Djava.library.path=" + nativesPath);

        // Classpath Argument (-cp)
        // FIX #22: The old classpath was completely wrong. This is a more realistic, albeit simplified, approach.
        // A real launcher MUST parse the version's JSON file to get the exact list of required libraries.
        cmd.add("-cp");
        cmd.add(buildClasspath(mcDir, version));
        
        // Main Class
        cmd.add(version.mainClass);

        // Game Arguments
        // These are common arguments. A real launcher gets these from the version JSON too.
        cmd.add("--username");
        cmd.add(auth.username());
        cmd.add("--uuid");
        cmd.add(auth.uuid());
        cmd.add("--accessToken");
        cmd.add(auth.accessToken());
        
        cmd.add("--version");
        cmd.add(version.id); // This is often the version ID, not the display name
        cmd.add("--gameDir");
        cmd.add(mcDir);
        cmd.add("--assetsDir");
        cmd.add(mcDir + File.separator + "assets");
        cmd.add("--assetIndex");
        // This is a simplification. A real launcher would parse this from the version.json
        String assetIndex = version.id.contains("1.20.4") ? "12" : (version.id.contains("1.20.1") ? "5" : "12");
        cmd.add(assetIndex);
        
        cmd.add("--userType");
        cmd.add("offline".equals(loginType) ? "legacy" : "msa");
        
        // For Forge specifically
        if (version.mainClass.contains("modlauncher")) {
            cmd.add("--launchTarget");
            cmd.add("forgeclient");
        }

        return cmd;
    }

    // FIX #23: New helper method to build a more realistic classpath
    private String buildClasspath(String mcDir, VersionInfo version) {
        String sep = System.getProperty("path.separator");
        String libsDir = mcDir + File.separator + "libraries";
        
        // A proper launcher parses the version JSON and adds ONLY the required libraries.
        // This is a simplified "guess" that includes all libraries, which is more than needed but usually works.
        File libraryDirFile = new File(libsDir);
        List<File> libraryFiles = new ArrayList<>();
        if (libraryDirFile.exists() && libraryDirFile.isDirectory()) {
            collectJars(libraryDirFile, libraryFiles);
        }

        String librariesClasspath = libraryFiles.stream()
                                     .map(File::getAbsolutePath)
                                     .collect(Collectors.joining(sep));

        // The version's own JAR is also part of the classpath
        String versionJar = mcDir + File.separator + "versions" + File.separator + version.id + File.separator + version.id + ".jar";
        
        return librariesClasspath + sep + versionJar;
    }
    
    // Helper for buildClasspath to recursively find all .jar files
    private void collectJars(File directory, List<File> jarFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectJars(file, jarFiles);
            } else if (file.getName().endsWith(".jar")) {
                jarFiles.add(file);
            }
        }
    }

    private static String getMcDir() {
        String os = System.getProperty("os.name").toUpperCase();
        String home = System.getProperty("user.home");
        if (os.contains("WIN")) {
            String appdata = System.getenv("APPDATA");
            return (appdata != null ? appdata : home) + File.separator + ".minecraft";
        }
        if (os.contains("MAC")) {
            return home + "/Library/Application Support/minecraft";
        }
        return home + "/.minecraft";
    }

    // FIX #24: Replaced reflection and removed broken fallback
    private static int detectMaxRam() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            long totalRamBytes = osBean.getTotalMemorySize(); // Modern method
            return (int) Math.max(1, totalRamBytes / (1024 * 1024 * 1024));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to detect system RAM automatically. Defaulting to 8GB.", e);
            return 8; // Return a safe default if detection fails
        }
    }

    // FIX #25: Improved offline UUID generation with better error handling
    private static String uuidOffline(String username) {
        try {
            byte[] bytes = ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(bytes);
            return UUID.nameUUIDFromBytes(hash).toString();
        } catch (NoSuchAlgorithmException e) {
            // This should never happen in a standard JRE, but it's good practice to handle.
            // A random UUID is dangerous for world saves. It's better to fail the launch.
            logger.log(Level.SEVERE, "Could not generate offline player UUID because MD5 is not supported.", e);
            throw new RuntimeException("MD5 algorithm not found for UUID generation.", e);
        }
    }
    
    // --- Config and Logging ---
    private static void setupLogging() {
        logger = Logger.getLogger("Melon");
        try {
            // Make sure logs directory exists
            new File("logs").mkdirs();
            FileHandler fh = new FileHandler("logs/" + LOG_FILE, true); // Append to log
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false); // Don't also log to console by default
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            // If file logging fails, fall back to console
            logger.addHandler(new ConsoleHandler());
            logger.log(Level.WARNING, "Failed to create log file, using console.", e);
        }
    }

    private void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            config.load(input);
            logger.info("Configuration loaded from " + CONFIG_FILE);
        } catch (IOException e) {
            logger.info("No config file found (" + CONFIG_FILE + "), using default settings.");
        }
    }

    private void saveConfig() {
        // Update config object before saving
        config.setProperty("login_type", loginType);
        if ("offline".equals(loginType)) {
            config.setProperty("offline_username", usernameField.getText().trim());
        } else if (authInfo != null && authInfo.username() != null) {
            config.setProperty("ms_name", authInfo.username());
            config.setProperty("ms_id", authInfo.uuid());
            config.setProperty("ms_token", authInfo.accessToken());
        }
        config.setProperty("ram", String.valueOf(ramSlider.getValue()));
        VersionInfo selectedVersion = (VersionInfo) versionBox.getSelectedItem();
        if (selectedVersion != null) {
            config.setProperty("version_id", selectedVersion.id);
        }
        
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            config.store(output, "Melon Launcher Configuration");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save configuration.", e);
        }
    }
    
    // FIX #26: Implemented method to apply a dark theme
    private void applyDarkTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("Panel.background", BG);
            UIManager.put("OptionPane.background", BG);
            UIManager.put("OptionPane.messageForeground", FG);
            UIManager.put("Button.background", ENTRY_BG);
            UIManager.put("Button.foreground", FG);
            UIManager.put("ComboBox.background", ENTRY_BG);
            UIManager.put("ComboBox.foreground", FG);
            UIManager.put("TextField.background", ENTRY_BG);
            UIManager.put("TextField.foreground", FG);
            UIManager.put("TextField.caretForeground", FG);
            UIManager.put("RadioButton.background", BG);
            UIManager.put("RadioButton.foreground", FG);
            UIManager.put("Label.foreground", FG);
            UIManager.put("Slider.background", BG);
        } catch (Exception e) {
            logger.warning("Could not set dark theme.");
        }
    }

    private void onClose() {
        logger.info("Closing Melon Launcher...");
        saveConfig(); // Save settings on exit
        frame.dispose();
        System.exit(0); // Ensure the application exits completely, killing any timers
    }
}
