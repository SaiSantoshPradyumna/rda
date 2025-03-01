import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class SingleLauncher extends JFrame {
    private JRadioButton hostRadioButton;
    private JRadioButton clientRadioButton;
    private JTextField ipField;
    private JTextField portField;
    private JButton startButton;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SingleLauncher launcher = new SingleLauncher();
            launcher.setVisible(true);
        });
    }

    public SingleLauncher() {
        super("Remote Desktop Launcher (Single File)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(350, 200);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.NONE;

        JLabel questionLabel = new JLabel("Do you want to be the Host or the Client?");
        add(questionLabel, gbc);

        // Host / Client radio buttons
        hostRadioButton = new JRadioButton("Host");
        clientRadioButton = new JRadioButton("Client");
        ButtonGroup group = new ButtonGroup();
        group.add(hostRadioButton);
        group.add(clientRadioButton);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        add(hostRadioButton, gbc);

        gbc.gridx = 1;
        add(clientRadioButton, gbc);

        // IP / Port fields (only needed if Client is chosen)
        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Host IP:"), gbc);

        ipField = new JTextField("127.0.0.1", 10);
        gbc.gridx = 1;
        add(ipField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        add(new JLabel("Port:"), gbc);

        portField = new JTextField("5000", 5);
        gbc.gridx = 1;
        add(portField, gbc);

        // Start button
        startButton = new JButton("Start");
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        add(startButton, gbc);

        // Default selection
        hostRadioButton.setSelected(true);
        ipField.setEnabled(false);
        portField.setEnabled(false);

        // Listen for radio button changes
        hostRadioButton.addActionListener(e -> {
            ipField.setEnabled(false);
            portField.setEnabled(false);
        });
        clientRadioButton.addActionListener(e -> {
            ipField.setEnabled(true);
            portField.setEnabled(true);
        });

        // Start button action:
        startButton.addActionListener(e -> {
            if (hostRadioButton.isSelected()) {
                // Hide this launcher and start the Host GUI
                setVisible(false);
                SwingUtilities.invokeLater(() -> {
                    HostGUI hostGUI = new HostGUI();
                    // Optional: auto-maximize 
                    hostGUI.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    hostGUI.setVisible(true);
                });
            } else if (clientRadioButton.isSelected()) {
                // Gather IP and Port, then start the client
                String hostIP = ipField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                setVisible(false); // hide launcher
                new Thread(() -> {
                    RemoteDesktopClient client = new RemoteDesktopClient();
                    client.startClient(hostIP, port);
                }).start();
            }
        });
    }

    // ──────────────────────────────────────────────────────
    // HOST-SIDE CODE
    // ──────────────────────────────────────────────────────

    static class HostGUI extends JFrame {
        private DefaultListModel<ClientHandler> clientListModel;
        private JList<ClientHandler> clientJList;
        private JLabel imageLabel;
        private ServerThread serverThread;

        // Optionally set a default size
        private static final int WIDTH = 800;
        private static final int HEIGHT = 600;

        public HostGUI() {
            super("Remote Desktop Host (Single File)");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(WIDTH, HEIGHT);
            setLayout(new BorderLayout());

            // Left panel: list of connected clients
            clientListModel = new DefaultListModel<>();
            clientJList = new JList<>(clientListModel);
            clientJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            clientJList.addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        ClientHandler selectedClient = clientJList.getSelectedValue();
                        if (selectedClient != null) {
                            // Show the last screenshot from that client
                            updateClientScreen(selectedClient, selectedClient.getLastReceivedImage());
                            imageLabel.requestFocusInWindow();
                        }
                    }
                }
            });

            JScrollPane listScrollPane = new JScrollPane(clientJList);
            listScrollPane.setPreferredSize(new Dimension(200, HEIGHT));
            add(listScrollPane, BorderLayout.WEST);

            // Center panel for displaying the remote screen
            imageLabel = new JLabel();
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setFocusable(true);

            // Mouse events → forward to client
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { sendMouseEvent(e); }
                @Override
                public void mouseReleased(MouseEvent e) { sendMouseEvent(e); }
                @Override
                public void mouseMoved(MouseEvent e) { sendMouseEvent(e); }
                @Override
                public void mouseDragged(MouseEvent e) { sendMouseEvent(e); }
            };
            imageLabel.addMouseMotionListener(mouseAdapter);
            imageLabel.addMouseListener(mouseAdapter);

            // Keyboard events → forward to client
            imageLabel.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) { sendKeyEvent(e); }
                @Override
                public void keyReleased(KeyEvent e) { sendKeyEvent(e); }
            });

            add(imageLabel, BorderLayout.CENTER);

            // Start the server on port 5000 by default
            serverThread = new ServerThread(5000, this);
            serverThread.start();
        }

        // Called when a new client connects → add to JList
        public void addClient(ClientHandler clientHandler) {
            SwingUtilities.invokeLater(() -> {
                clientListModel.addElement(clientHandler);
            });
        }

        // Scale the incoming image to fill the label, then display
        public void updateClientScreen(ClientHandler clientHandler, ImageIcon newImage) {
            if (clientJList.getSelectedValue() != clientHandler || newImage == null) {
                return;
            }
            int w = imageLabel.getWidth();
            int h = imageLabel.getHeight();
            if (w > 0 && h > 0) {
                Image scaled = newImage.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaled);
                imageLabel.setIcon(scaledIcon);
            } else {
                // If label is not sized yet, just show the original
                imageLabel.setIcon(newImage);
            }
        }

        // Forward mouse event
        private void sendMouseEvent(MouseEvent e) {
            ClientHandler selectedClient = clientJList.getSelectedValue();
            if (selectedClient != null) {
                selectedClient.sendMouseEvent(e, imageLabel.getWidth(), imageLabel.getHeight());
            }
        }

        // Forward key event
        private void sendKeyEvent(KeyEvent e) {
            ClientHandler selectedClient = clientJList.getSelectedValue();
            if (selectedClient != null) {
                selectedClient.sendKeyEvent(e);
            }
        }
    }

    // Background server that accepts new clients
    static class ServerThread extends Thread {
        private int port;
        private HostGUI hostGUI;

        public ServerThread(int port, HostGUI hostGUI) {
            this.port = port;
            this.hostGUI = hostGUI;
        }

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, hostGUI);
                    handler.start();
                    hostGUI.addClient(handler);
                }
            } catch (IOException e) {
                // No console output
            }
        }
    }

    // Handles a single remote client
    static class ClientHandler extends Thread {
        private Socket socket;
        private HostGUI hostGUI;
        private ObjectInputStream inputStream;
        private ObjectOutputStream outputStream;
        private ImageIcon lastReceivedImage;
        private String clientName = "Unknown Client";

        public ClientHandler(Socket socket, HostGUI hostGUI) {
            this.socket = socket;
            this.hostGUI = hostGUI;
        }

        public ImageIcon getLastReceivedImage() {
            return lastReceivedImage;
        }

        @Override
        public void run() {
            try {
                outputStream = new ObjectOutputStream(socket.getOutputStream());
                outputStream.flush();
                inputStream = new ObjectInputStream(socket.getInputStream());

                // First message is client's hostname
                Object firstObj = inputStream.readObject();
                if (firstObj instanceof String) {
                    clientName = (String) firstObj;
                }

                // Continuously receive new screenshots
                while (true) {
                    Object data = inputStream.readObject();
                    if (data instanceof byte[]) {
                        byte[] imageBytes = (byte[]) data;
                        ImageIcon icon = new ImageIcon(imageBytes);
                        lastReceivedImage = icon;
                        hostGUI.updateClientScreen(this, icon);
                    }
                }
            } catch (Exception e) {
                // No console output
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        // Converts the client handler to a user-friendly name in the JList
        @Override
        public String toString() {
            return clientName;
        }

        // Send mouse event
        public void sendMouseEvent(MouseEvent e, int labelWidth, int labelHeight) {
            try {
                if (outputStream != null) {
                    RemoteEvent remoteEvent = new RemoteEvent();
                    remoteEvent.type = RemoteEvent.Type.MOUSE;
                    remoteEvent.mouseID = e.getID();
                    remoteEvent.button = e.getButton();
                    remoteEvent.x = e.getX();
                    remoteEvent.y = e.getY();
                    remoteEvent.displayWidth = labelWidth;
                    remoteEvent.displayHeight = labelHeight;

                    outputStream.writeObject(remoteEvent);
                    outputStream.flush();
                }
            } catch (IOException ignored) {}
        }

        // Send key event
        public void sendKeyEvent(KeyEvent e) {
            try {
                if (outputStream != null) {
                    RemoteEvent remoteEvent = new RemoteEvent();
                    remoteEvent.type = RemoteEvent.Type.KEYBOARD;
                    remoteEvent.keyID = e.getID();
                    remoteEvent.keyCode = e.getKeyCode();
                    remoteEvent.keyChar = e.getKeyChar();

                    outputStream.writeObject(remoteEvent);
                    outputStream.flush();
                }
            } catch (IOException ignored) {}
        }
    }

    // ──────────────────────────────────────────────────────
    // CLIENT-SIDE CODE
    // ──────────────────────────────────────────────────────
    static class RemoteDesktopClient {
        private boolean running = true;

        public void startClient(String host, int port) {
            try {
                Socket socket = new Socket(host, port);

                // Show a small topmost window indicating the client is controlled
                String hostName = socket.getInetAddress().getHostName();
                showControlIndicator(hostName);

                ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                outputStream.flush();
                ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());

                // Send this machine's hostname to the host
                sendClientHostname(outputStream);

                // Thread to capture and send screenshots repeatedly
                Thread captureThread = new Thread(() -> {
                    try {
                        Robot robot = new Robot();
                        while (running) {
                            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                            Rectangle screenRect = new Rectangle(screenSize);
                            BufferedImage capture = robot.createScreenCapture(screenRect);

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(capture, "jpg", baos);
                            byte[] imageBytes = baos.toByteArray();

                            synchronized (outputStream) {
                                outputStream.writeObject(imageBytes);
                                outputStream.flush();
                            }
                            Thread.sleep(100); // ~10 fps
                        }
                    } catch (Exception e) {
                        // No console output
                    }
                });
                captureThread.start();

                Robot robot = new Robot();
                // Receive remote input events and replay them
                while (running) {
                    try {
                        Object eventObj = inputStream.readObject();
                        if (eventObj instanceof RemoteEvent) {
                            RemoteEvent remoteEvent = (RemoteEvent) eventObj;
                            handleRemoteEvent(remoteEvent, robot);
                        }
                    } catch (EOFException eof) {
                        running = false;
                    }
                }

                captureThread.join();
                inputStream.close();
                outputStream.close();
                socket.close();
            } catch (Exception e) {
                // No console output
            }
        }

        // Displays a small topmost window with the controlling host's name
        private void showControlIndicator(String hostName) {
            JFrame indicator = new JFrame("Controlled by " + hostName);
            indicator.setSize(300, 60);
            indicator.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            indicator.setAlwaysOnTop(true);
            indicator.setLayout(new BorderLayout());

            JLabel msg = new JLabel("Your system is being controlled by: " + hostName, SwingConstants.CENTER);
            indicator.add(msg, BorderLayout.CENTER);

            indicator.setLocationRelativeTo(null);
            indicator.setVisible(true);
        }

        // Sends local hostname to the host
        private void sendClientHostname(ObjectOutputStream outputStream) {
            try {
                String hostname;
                try {
                    hostname = InetAddress.getLocalHost().getHostName();
                } catch (Exception e) {
                    hostname = "Unknown Client";
                }
                outputStream.writeObject(hostname);
                outputStream.flush();
            } catch (IOException ignored) {}
        }

        // Replays remote mouse/keyboard events on this machine
        private void handleRemoteEvent(RemoteEvent event, Robot robot) {
            switch (event.type) {
                case MOUSE:
                    handleMouseEvent(event, robot);
                    break;
                case KEYBOARD:
                    handleKeyEvent(event, robot);
                    break;
            }
        }

        private void handleMouseEvent(RemoteEvent event, Robot robot) {
            // Scale from label coords to the real screen
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int realX = (int)((double)event.x / event.displayWidth * screenSize.width);
            int realY = (int)((double)event.y / event.displayHeight * screenSize.height);

            robot.mouseMove(realX, realY);

            // Replay button presses
            if (event.mouseID == MouseEvent.MOUSE_PRESSED) {
                if (event.button == MouseEvent.BUTTON1) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                } else if (event.button == MouseEvent.BUTTON3) {
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                }
            } else if (event.mouseID == MouseEvent.MOUSE_RELEASED) {
                if (event.button == MouseEvent.BUTTON1) {
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                } else if (event.button == MouseEvent.BUTTON3) {
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                }
            }
        }

        private void handleKeyEvent(RemoteEvent event, Robot robot) {
            if (event.keyID == KeyEvent.KEY_PRESSED) {
                robot.keyPress(event.keyCode);
            } else if (event.keyID == KeyEvent.KEY_RELEASED) {
                robot.keyRelease(event.keyCode);
            }
        }
    }

    // A simple serializable class for input events
    static class RemoteEvent implements Serializable {
        enum Type {
            MOUSE,
            KEYBOARD
        }

        public Type type;

        // Mouse data
        public int mouseID;   
        public int button;    
        public int x, y;
        public int displayWidth, displayHeight;

        // Keyboard data
        public int keyID;     
        public int keyCode;
        public char keyChar;
    }
}
