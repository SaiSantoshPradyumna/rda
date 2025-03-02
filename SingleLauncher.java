import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxEditor;
public class SingleLauncher extends JFrame {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SingleLauncher launcher = new SingleLauncher();
            launcher.setVisible(true);
        });
    }

    public SingleLauncher() {
        super("Remote Desktop Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(320, 150);
        setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton hostButton = new JButton("Start Host");
        JButton clientButton = new JButton("Start Client");

        add(hostButton);
        add(clientButton);

        hostButton.addActionListener(e -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                HostGUI hostGUI = new HostGUI(5000);
                hostGUI.setExtendedState(JFrame.MAXIMIZED_BOTH);
                hostGUI.setVisible(true);
            });
        });
        clientButton.addActionListener(e -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                ClientLauncher cl = new ClientLauncher();
                cl.setVisible(true);
            });
        });
    }

    // ---------------- Host Side classes ----------------

    static class HostGUI extends JFrame {
        private JComboBox<ClientHandler> clientCombo;
        private JLabel screenLabel;
        private JButton sendFileButton;
        private ServerThread serverThread;
        private int listeningPort;
    
        public HostGUI(int port) {
            super("Host – Remote Desktop");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1000, 700);
            setLayout(new BorderLayout());
            this.listeningPort = port;
    
            String localIp = "Unknown";
            try {
                localIp = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) { }
            
            JLabel hostIpLabel = new JLabel("Hosting on IP: " + localIp + "   Port: " + listeningPort, SwingConstants.CENTER);
    
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            JLabel connLabel = new JLabel("Connections:");
            clientCombo = new JComboBox<>();
            JButton openChatBtn = new JButton("Open Chat");
            openChatBtn.addActionListener(e -> {
                ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
                if (selected != null) {
                    selected.openOrCreateChatWindow();
                }
            });
            JButton stopConnBtn = new JButton("Stop Connection");
            stopConnBtn.addActionListener(e -> {
                ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
                if (selected != null) {
                    selected.stopConnection();
                }
            });
            sendFileButton = new JButton("Send File");
            sendFileButton.addActionListener(e -> {
                ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
                if (selected != null) {
                    JFileChooser fc = new JFileChooser();
                    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
                        long MAX_FILE_SIZE = 10 * 1024 * 1024;
                        if (file.length() <= MAX_FILE_SIZE) {
                            selected.sendFile(file);
                        } else {
                            JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit.");
                        }
                    }
                }
            });
            topPanel.add(connLabel);
            topPanel.add(clientCombo);
            topPanel.add(openChatBtn);
            topPanel.add(stopConnBtn);
            topPanel.add(sendFileButton);
    
            JPanel northPanel = new JPanel(new BorderLayout());
            northPanel.add(hostIpLabel, BorderLayout.NORTH);
            northPanel.add(topPanel, BorderLayout.SOUTH);
            add(northPanel, BorderLayout.NORTH);
    
            screenLabel = new JLabel();
            screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
            screenLabel.setVerticalAlignment(SwingConstants.CENTER);
            screenLabel.setBackground(Color.BLACK);
            screenLabel.setOpaque(true);
    
            MouseAdapter mouseAdapter = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { forwardMouseEvent(e); }
                public void mouseReleased(MouseEvent e) { forwardMouseEvent(e); }
                public void mouseMoved(MouseEvent e) { forwardMouseEvent(e); }
                public void mouseDragged(MouseEvent e) { forwardMouseEvent(e); }
            };
            screenLabel.addMouseListener(mouseAdapter);
            screenLabel.addMouseMotionListener(mouseAdapter);
            screenLabel.setFocusable(true);
            screenLabel.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && screenLabel.isDisplayable()) {
                    SwingUtilities.invokeLater(() -> screenLabel.requestFocusInWindow());
                }
            });
            screenLabel.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) { forwardKeyEvent(e); }
                public void keyReleased(KeyEvent e) { forwardKeyEvent(e); }
                public void keyTyped(KeyEvent e) { forwardKeyEvent(e); }
            });
            screenLabel.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { screenLabel.requestFocusInWindow(); }
            });
            add(screenLabel, BorderLayout.CENTER);
    
            serverThread = new ServerThread(listeningPort, this);
            serverThread.start();
        }
    
        public void updateClientScreen(ClientHandler handler, ImageIcon icon) {
            ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
            if (selected != handler || icon == null) { return; }
            int w = screenLabel.getWidth();
            int h = screenLabel.getHeight();
            if (w > 0 && h > 0) {
                Image scaled = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                screenLabel.setIcon(new ImageIcon(scaled));
            } else {
                screenLabel.setIcon(icon);
            }
        }
    
        public void addClientHandler(ClientHandler handler) {
            SwingUtilities.invokeLater(() -> clientCombo.addItem(handler));
        }
    
        private void forwardMouseEvent(MouseEvent e) {
            ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
            if (selected != null) { 
                selected.sendMouseEvent(e, screenLabel.getWidth(), screenLabel.getHeight()); 
            }
        }
    
        private void forwardKeyEvent(KeyEvent e) {
            ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
            if (selected != null) { 
                selected.sendKeyEvent(e); 
            }
        }
    }

    static class ServerThread extends Thread {
        private int port;
        private HostGUI hostGUI;
    
        public ServerThread(int port, HostGUI hostGUI) {
            this.port = port;
            this.hostGUI = hostGUI;
        }
    
        public void run() {
            try (ServerSocket ss = new ServerSocket(port)) {
                while (true) {
                    Socket clientSocket = ss.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, hostGUI);
                    handler.start();
                    hostGUI.addClientHandler(handler);
                }
            } catch (IOException e) { 
                e.printStackTrace(); 
            }
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private HostGUI hostGUI;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private ImageIcon lastReceivedImage;
        private String clientName = "Unknown Client";
        private ChatWindow chatWindow = null;
        private volatile boolean running = true;
    
        public ClientHandler(Socket socket, HostGUI hostGUI) {
            this.socket = socket;
            this.hostGUI = hostGUI;
        }
    
        public ImageIcon getLastReceivedImage() { 
            return lastReceivedImage; 
        }
    
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
    
                // Send host name to client
                String hostName;
                try {
                    hostName = InetAddress.getLocalHost().getHostName();
                } catch (Exception ex) { 
                    hostName = "Unknown Host"; 
                }
                out.writeObject(hostName);
                out.flush();
    
                // Receive client hostname/name
                Object first = in.readObject();
                if (first instanceof String) { 
                    clientName = (String) first; 
                }
    
                while (running) {
                    Object obj = in.readObject();
                    if (obj instanceof byte[]) {
                        lastReceivedImage = new ImageIcon((byte[]) obj);
                        hostGUI.updateClientScreen(this, lastReceivedImage);
                    } else if (obj instanceof RemoteMessage) {
                        RemoteMessage msg = (RemoteMessage) obj;
                        if (msg.type == RemoteMessage.MessageType.CHAT) {
                            if (chatWindow != null) { 
                                chatWindow.appendChatMessage(clientName + ": " + msg.chatText); 
                            }
                        } else if (msg.type == RemoteMessage.MessageType.FILE) {
                            SwingUtilities.invokeLater(() -> {
                                int choice = JOptionPane.showConfirmDialog(hostGUI, 
                                     clientName + " sent file: " + msg.fileName + "\nSave file?", 
                                     "File received", JOptionPane.YES_NO_OPTION);
                                if (choice == JOptionPane.YES_OPTION) {
                                    JFileChooser fc = new JFileChooser();
                                    fc.setSelectedFile(new File(msg.fileName));
                                    if (fc.showSaveDialog(hostGUI) == JFileChooser.APPROVE_OPTION) {
                                        File saveFile = fc.getSelectedFile();
                                        try (FileOutputStream fos = new FileOutputStream(saveFile)) { 
                                            fos.write(msg.fileData); 
                                        } catch (IOException ex) { }
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                // You might want to log the error here
            } finally {
                stopConnection();
            }
        }
    
        public String toString() { 
            return clientName; 
        }
    
        public void sendMouseEvent(MouseEvent e, int labelW, int labelH) {
            try {
                if (out == null) { return; }
                RemoteEvent evt = new RemoteEvent();
                evt.type = RemoteEvent.Type.MOUSE;
                evt.mouseID = e.getID();
                evt.button = e.getButton();
                evt.x = e.getX();
                evt.y = e.getY();
                evt.displayWidth = labelW;
                evt.displayHeight = labelH;
                out.writeObject(evt);
                out.flush();
            } catch (IOException ex) { }
        }
    
        public void sendKeyEvent(KeyEvent e) {
            try {
                if (out == null) { return; }
                RemoteEvent evt = new RemoteEvent();
                evt.type = RemoteEvent.Type.KEYBOARD;
                evt.keyID = e.getID();
                evt.keyCode = e.getKeyCode();
                evt.keyChar = e.getKeyChar();
                out.writeObject(evt);
                out.flush();
            } catch (IOException ex) { }
        }
    
        public void sendChatMessage(String text) {
            try {
                if (out == null) { return; }
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch (IOException ex) { }
        }
    
        public void sendFile(File file) {
            try {
                if (out == null) { return; }
                byte[] data = readFile(file);
                if (data == null) { return; }
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.FILE;
                msg.fileName = file.getName();
                msg.fileData = data;
                out.writeObject(msg);
                out.flush();
            } catch (IOException ex) { }
        }
    
        private byte[] readFile(File file) {
            try {
                byte[] fileBytes = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(fileBytes);
                    if (read != fileBytes.length) { return null; }
                    return fileBytes;
                }
            } catch (IOException ex) { 
                return null; 
            }
        }
    
        public void openOrCreateChatWindow() {
            if (chatWindow == null) { 
                chatWindow = new ChatWindow(this, clientName); 
            }
            if (!chatWindow.isVisible()) { 
                chatWindow.setVisible(true); 
            }
        }
    
        public void stopConnection() {
            running = false;
            try { if (in != null) { in.close(); } } catch (IOException ex) { }
            try { if (out != null) { out.close(); } } catch (IOException ex) { }
            try { if (socket != null && !socket.isClosed()) { socket.close(); } } catch (IOException ex) { }
        }
    }

    static class ChatWindow extends JFrame {
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private ClientHandler handler;
        private String clientName;
    
        public ChatWindow(ClientHandler handler, String clientName) {
            super("Chat with " + clientName);
            this.handler = handler;
            this.clientName = clientName;
            setSize(400, 300);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            add(new JScrollPane(chatArea), BorderLayout.CENTER);
    
            chatInput = new JTextField();
            sendChatButton = new JButton("Send");
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(chatInput, BorderLayout.CENTER);
            inputPanel.add(sendChatButton, BorderLayout.EAST);
            add(inputPanel, BorderLayout.SOUTH);
    
            sendChatButton.addActionListener(e -> {
                String text = chatInput.getText().trim();
                if (!text.isEmpty()) {
                    appendChatMessage("You: " + text);
                    handler.sendChatMessage(text);
                    chatInput.setText("");
                }
            });
        }
    
        public void appendChatMessage(String msg) { 
            chatArea.append(msg + "\n"); 
        }
    }

    // ---------------- Client Side classes ----------------

    // Make RemoteDesktopClient a static nested class so it can be used
    // from ClientLauncher without requiring an instance of SingleLauncher.
    static class RemoteDesktopClient {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private ClientGUI clientGUI;
        private ConnectionEntry conn;
        private boolean running = false;
        private ClientLauncher launcher; // used to re-open launcher on failure
        private ClientChatWindow chatWindow; // Added missing chat window field
    
        public void startClient(String host, int port, ConnectionEntry conn, ClientLauncher launcher) {
            this.conn = conn;
            this.launcher = launcher;
            try {
                socket = new Socket(host, port);
                conn.setOnline(true);
                clientGUI = new ClientGUI();
                clientGUI.setController(this);
    
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
    
                // Receive host name and set it in client GUI
                String hostName = (String) in.readObject();
                clientGUI.setControlledBy(hostName);
                sendClientHostname();
    
                SwingUtilities.invokeLater(() -> {
                    clientGUI.setVisible(true);
                    clientGUI.setExtendedState(JFrame.NORMAL);
                    clientGUI.toFront();
                    clientGUI.requestFocus();
                    openChatWindow();
                });
    
                running = true;
                // You can implement a loop to receive remote events or screenshots here
    
            } catch (Exception e) {
                conn.setOnline(false);
                SwingUtilities.invokeLater(() -> launcher.setVisible(true)); // Reopen launcher on failure
            }
        }
    
        public void stopConnection() {
            running = false;
            try { if (in != null) in.close(); } catch (IOException ex) {}
            try { if (out != null) out.close(); } catch (IOException ex) {}
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ex) {}
    
            SwingUtilities.invokeLater(() -> {
                clientGUI.showConnectionStopped();
                clientGUI.dispose(); // Close ClientGUI after stopping connection
                if (launcher != null) {
                    launcher.setVisible(true); // Reopen ClientLauncher
                } else {
                    new ClientLauncher().setVisible(true);
                }
            });
    
            conn.setOnline(false);
        }
    
        private void sendClientHostname() {
            try {
                out.writeObject(InetAddress.getLocalHost().getHostName());
                out.flush();
            } catch (IOException e) { }
        }
    
        private void handleRemoteEvent(RemoteEvent evt, Robot robot) {
            if (evt.type == RemoteEvent.Type.MOUSE) {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int realX = (int) (((double) evt.x / evt.displayWidth) * screenSize.width);
                int realY = (int) (((double) evt.y / evt.displayHeight) * screenSize.height);
                robot.mouseMove(realX, realY);
                if (evt.mouseID == MouseEvent.MOUSE_PRESSED) {
                    if (evt.button == MouseEvent.BUTTON1) { 
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); 
                    } else if (evt.button == MouseEvent.BUTTON3) { 
                        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK); 
                    }
                } else if (evt.mouseID == MouseEvent.MOUSE_RELEASED) {
                    if (evt.button == MouseEvent.BUTTON1) { 
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); 
                    } else if (evt.button == MouseEvent.BUTTON3) { 
                        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK); 
                    }
                }
            } else if (evt.type == RemoteEvent.Type.KEYBOARD) {
                if (evt.keyID == KeyEvent.KEY_PRESSED) { 
                    robot.keyPress(evt.keyCode); 
                } else if (evt.keyID == KeyEvent.KEY_RELEASED) { 
                    robot.keyRelease(evt.keyCode); 
                }
            }
        }
    
        public void sendChatMessage(String text) {
            try {
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch (IOException ex) { }
        }
    
        public void sendFileMessage(File file) {
            try {
                byte[] data = readFile(file);
                if (data == null) { return; }
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.FILE;
                msg.fileName = file.getName();
                msg.fileData = data;
                out.writeObject(msg);
                out.flush();
            } catch (IOException ex) { }
        }
    
        private byte[] readFile(File file) {
            try {
                byte[] fileBytes = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(fileBytes);
                    if (read != fileBytes.length) { return null; }
                    return fileBytes;
                }
            } catch (IOException ex) { 
                return null; 
            }
        }
    
        public void openChatWindow() {
            if (chatWindow == null) { 
                chatWindow = new ClientChatWindow(this); 
            }
            if (!chatWindow.isVisible()) { 
                chatWindow.setVisible(true); 
            }
        }
    }

    static class ClientGUI extends JFrame {
        private JButton sendFileButton;
        private JButton stopConnButton;
        private JButton openChatButton;
        private RemoteDesktopClient controller;
        private JLabel infoLabel;
    
        public ClientGUI() {
            super("Client – Remote Controlled");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(600, 400);
            setLayout(new BorderLayout());
    
            infoLabel = new JLabel("Your system is being remotely controlled.", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);
    
            JPanel btnPanel = new JPanel();
            openChatButton = new JButton("Open Chat");
            openChatButton.addActionListener(e -> { if (controller != null) { controller.openChatWindow(); } });
            sendFileButton = new JButton("Send File");
            sendFileButton.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    long MAX_FILE_SIZE = 10 * 1024 * 1024;
                    if (file.length() <= MAX_FILE_SIZE) { 
                        if (controller != null) { controller.sendFileMessage(file); } 
                    } else { 
                        JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit."); 
                    }
                }
            });
            stopConnButton = new JButton("Stop Connection");
            stopConnButton.addActionListener(e -> { if (controller != null) { controller.stopConnection(); } });
    
            btnPanel.add(openChatButton);
            btnPanel.add(sendFileButton);
            btnPanel.add(stopConnButton);
            add(btnPanel, BorderLayout.SOUTH);
        }
    
        public void setController(RemoteDesktopClient c) { 
            controller = c; 
        }
    
        public void setControlledBy(String hostName) { 
            infoLabel.setText("Your system is being remotely controlled by " + hostName); 
        }
    
        public void showConnectionStopped() { 
            infoLabel.setText("Connection has been stopped."); 
        }
    }

    static class ClientChatWindow extends JFrame {
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private RemoteDesktopClient controller;
    
        public ClientChatWindow(RemoteDesktopClient controller) {
            super("Chat");
            this.controller = controller;
            setSize(600, 400);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            add(new JScrollPane(chatArea), BorderLayout.CENTER);
    
            chatInput = new JTextField();
            sendChatButton = new JButton("Send");
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(chatInput, BorderLayout.CENTER);
            inputPanel.add(sendChatButton, BorderLayout.EAST);
            add(inputPanel, BorderLayout.SOUTH);
    
            sendChatButton.addActionListener(e -> {
                String text = chatInput.getText().trim();
                if (!text.isEmpty()) {
                    appendChatMessage("You: " + text);
                    controller.sendChatMessage(text);
                    chatInput.setText("");
                }
            });
        }
    
        public void appendChatMessage(String msg) { 
            chatArea.append(msg + "\n"); 
        }
    }

    // ---------------- Common classes ----------------

    static class RemoteEvent implements Serializable {
        enum Type { MOUSE, KEYBOARD }
        public Type type;
        public int mouseID;
        public int button;
        public int x, y;
        public int displayWidth, displayHeight;
        public int keyID;
        public int keyCode;
        public char keyChar;
    }

    static class RemoteMessage implements Serializable {
        enum MessageType { CHAT, FILE }
        public MessageType type;
        public String chatText;
        public String fileName;
        public byte[] fileData;
    }

    static class ConnectionEntry {
        private String ip;
        private int port;
        private boolean online;
    
        public ConnectionEntry(String ip, int port) {
            this.ip = ip;
            this.port = port;
            this.online = false;
        }
    
        public String getIp() { 
            return ip; 
        }
    
        public int getPort() { 
            return port; 
        }
    
        public void setOnline(boolean online) { 
            this.online = online; 
        }
    
        public boolean isOnline() { 
            return online; 
        }
    
        public String toString() { 
            return ip + ":" + port + " - " + (online ? "Online" : "Offline"); 
        }
    }

    static class ClientLauncher extends JFrame {
        private DefaultComboBoxModel<ConnectionEntry> comboModel;
        private JComboBox<ConnectionEntry> connectionCombo;
        private JTextField portField;
        private JButton connectBtn;
        private JLabel statusLabel;
        private static final String CONNECTIONS_FILE = "connections.txt";
    
        public ClientLauncher() {
            super("Client Launcher");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400, 170);
            setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
    
            comboModel = new DefaultComboBoxModel<>();
            connectionCombo = new JComboBox<>(comboModel);
            connectionCombo.setEditable(true);
            connectionCombo.setEditor(new BasicComboBoxEditor() {
                @Override
                public void setItem(Object anObject) {
                    if (anObject instanceof ConnectionEntry) {
                        ConnectionEntry ce = (ConnectionEntry) anObject;
                        editor.setText(ce.getIp());
                    } else if (anObject != null) {
                        editor.setText(anObject.toString());
                    } else {
                        editor.setText("");
                    }
                }
    
                @Override
                public Object getItem() {
                    return editor.getText();
                }
            });
    
            connectionCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    if (value instanceof ConnectionEntry) {
                        ConnectionEntry ce = (ConnectionEntry) value;
                        value = ce.getIp();
                    }
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                }
            });
    
            portField = new JTextField("5000", 6);
            connectBtn = new JButton("Connect");
    
            add(new JLabel("IP:"));
            add(connectionCombo);
            add(new JLabel("Port:"));
            add(portField);
            add(connectBtn);
            statusLabel = new JLabel(" ");
            add(statusLabel);
    
            List<ConnectionEntry> saved = loadConnections();
            if (saved.isEmpty()) {
                try {
                    String defaultIP = InetAddress.getLocalHost().getHostAddress();
                    ConnectionEntry entry = new ConnectionEntry(defaultIP, Integer.parseInt(portField.getText().trim()));
                    comboModel.addElement(entry);
                } catch (Exception ex) {
                    comboModel.addElement(new ConnectionEntry("127.0.0.1", Integer.parseInt(portField.getText().trim())));
                }
            } else {
                for (ConnectionEntry ce : saved) {
                    comboModel.addElement(ce);
                }
            }
    
            updateConnectionCounts();
    
            connectBtn.addActionListener(e -> {
                String ip = connectionCombo.getEditor().getItem().toString().trim();
                int port;
                try {
                    port = Integer.parseInt(portField.getText().trim());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid port number");
                    return;
                }
    
                ConnectionEntry selected = null;
                for (int i = 0; i < comboModel.getSize(); i++) {
                    ConnectionEntry ce = comboModel.getElementAt(i);
                    if (ce.getIp().equals(ip) && ce.getPort() == port) {
                        selected = ce;
                        break;
                    }
                }
    
                if (selected == null) {
                    selected = new ConnectionEntry(ip, port);
                    comboModel.addElement(selected);
                }
    
                saveConnections(getAllEntries());
    
                // Hide ClientLauncher window, but keep it ready to reopen later
                setVisible(false);
    
                RemoteDesktopClient client = new RemoteDesktopClient();
                client.startClient(ip, port, selected, this);
            });
        }
    
        private List<ConnectionEntry> getAllEntries() {
            List<ConnectionEntry> list = new ArrayList<>();
            for (int i = 0; i < comboModel.getSize(); i++) {
                list.add(comboModel.getElementAt(i));
            }
            return list;
        }
    
        private void updateConnectionCounts() {
            int online = 0;
            int offline = 0;
            for (int i = 0; i < comboModel.getSize(); i++) {
                ConnectionEntry ce = comboModel.getElementAt(i);
                if (ce.isOnline()) { 
                    online++; 
                } else { 
                    offline++; 
                }
            }
            statusLabel.setText("Online: " + online + "   Offline: " + offline);
        }
    
        private List<ConnectionEntry> loadConnections() {
            List<ConnectionEntry> list = new ArrayList<>();
            File file = new File(CONNECTIONS_FILE);
            if (!file.exists()) { return list; }
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.trim().split(":");
                    if (parts.length == 2) {
                        String ip = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        list.add(new ConnectionEntry(ip, port));
                    }
                }
            } catch (IOException | NumberFormatException ex) { }
            return list;
        }
    
        private void saveConnections(List<ConnectionEntry> list) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(CONNECTIONS_FILE))) {
                for (ConnectionEntry ce : list) { 
                    pw.println(ce.getIp() + ":" + ce.getPort()); 
                }
            } catch (IOException ex) { 
                ex.printStackTrace(); 
            }
        }
    }

}
