import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.DefaultComboBoxModel;

public class SingleLauncher extends JFrame {
    private JTextField ipField;
    private JTextField portField;
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
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        JLabel ipLabel = new JLabel("Client Connect IP:");
        ipField = new JTextField("127.0.0.1", 10);
        JLabel portLabel = new JLabel("Port:");
        portField = new JTextField("5000", 5);
        JButton hostButton = new JButton("Start Host");
        JButton clientButton = new JButton("Start Client");
        gbc.gridx = 0; gbc.gridy = 0;
        add(ipLabel, gbc);
        gbc.gridx = 1;
        add(ipField, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        add(portLabel, gbc);
        gbc.gridx = 1;
        add(portField, gbc);
        gbc.gridx = 0; gbc.gridy = 2;
        add(hostButton, gbc);
        gbc.gridx = 1;
        add(clientButton, gbc);
        hostButton.addActionListener((ActionEvent e) -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                int port = Integer.parseInt(portField.getText().trim());
                HostGUI hostGUI = new HostGUI(port);
                hostGUI.setExtendedState(JFrame.MAXIMIZED_BOTH);
                hostGUI.setVisible(true);
            });
        });
        clientButton.addActionListener((ActionEvent e) -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                ClientLauncher cl = new ClientLauncher();
                cl.setVisible(true);
            });
        });
    }
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
            try { localIp = InetAddress.getLocalHost().getHostAddress(); } catch(UnknownHostException ex) {}
            JLabel hostIpLabel = new JLabel("Hosting on IP: " + localIp + "   Port: " + listeningPort, SwingConstants.CENTER);
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            JLabel connLabel = new JLabel("Connections:");
            clientCombo = new JComboBox<>();
            JButton openChatBtn = new JButton("Open Chat");
            openChatBtn.addActionListener(e -> {
                ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
                if(selected != null) { selected.openOrCreateChatWindow(); }
            });
            JButton stopConnBtn = new JButton("Stop Connection");
            stopConnBtn.addActionListener(e -> {
                ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
                if(selected != null) { selected.stopConnection(); }
            });
            sendFileButton = new JButton("Send File");
            sendFileButton.addActionListener(e -> {
                ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
                if(selected != null) {
                    JFileChooser fc = new JFileChooser();
                    if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
                        final long MAX_FILE_SIZE = 10 * 1024 * 1024;
                        if(file.length() <= MAX_FILE_SIZE) { selected.sendFile(file); }
                        else { JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit."); }
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
            MouseAdapter mouseAdapter = new MouseAdapter(){
                public void mousePressed(MouseEvent e){ forwardMouseEvent(e); }
                public void mouseReleased(MouseEvent e){ forwardMouseEvent(e); }
                public void mouseMoved(MouseEvent e){ forwardMouseEvent(e); }
                public void mouseDragged(MouseEvent e){ forwardMouseEvent(e); }
            };
            screenLabel.addMouseListener(mouseAdapter);
            screenLabel.addMouseMotionListener(mouseAdapter);
            screenLabel.setFocusable(true);
            screenLabel.addHierarchyListener(e -> {
                if((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && screenLabel.isDisplayable()){
                    SwingUtilities.invokeLater(() -> screenLabel.requestFocusInWindow());
                }
            });
            screenLabel.addKeyListener(new KeyAdapter(){
                public void keyPressed(KeyEvent e){ forwardKeyEvent(e); }
                public void keyReleased(KeyEvent e){ forwardKeyEvent(e); }
                public void keyTyped(KeyEvent e){ forwardKeyEvent(e); }
            });
            screenLabel.addMouseListener(new MouseAdapter(){
                public void mouseClicked(MouseEvent e){ screenLabel.requestFocusInWindow(); }
            });
            add(screenLabel, BorderLayout.CENTER);
            serverThread = new ServerThread(listeningPort, this);
            serverThread.start();
        }
        public void updateClientScreen(ClientHandler handler, ImageIcon icon) {
            ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
            if(selected != handler || icon == null) return;
            int w = screenLabel.getWidth();
            int h = screenLabel.getHeight();
            if(w > 0 && h > 0) {
                Image scaled = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                screenLabel.setIcon(new ImageIcon(scaled));
            } else { screenLabel.setIcon(icon); }
        }
        public void addClientHandler(ClientHandler handler) {
            SwingUtilities.invokeLater(() -> { clientCombo.addItem(handler); });
        }
        private void forwardMouseEvent(MouseEvent e) {
            ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
            if(selected != null) { selected.sendMouseEvent(e, screenLabel.getWidth(), screenLabel.getHeight()); }
        }
        private void forwardKeyEvent(KeyEvent e) {
            ClientHandler selected = (ClientHandler) clientCombo.getSelectedItem();
            if(selected != null) { selected.sendKeyEvent(e); }
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
            try(ServerSocket ss = new ServerSocket(port)) {
                while(true) {
                    Socket clientSocket = ss.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, hostGUI);
                    handler.start();
                    hostGUI.addClientHandler(handler);
                }
            } catch(IOException e) {}
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
        public ImageIcon getLastReceivedImage() { return lastReceivedImage; }
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                Object first = in.readObject();
                if(first instanceof String) { clientName = (String) first; }
                while(running) {
                    Object obj = in.readObject();
                    if(obj instanceof byte[]) {
                        lastReceivedImage = new ImageIcon((byte[])obj);
                        hostGUI.updateClientScreen(this, lastReceivedImage);
                    } else if(obj instanceof RemoteMessage) {
                        RemoteMessage msg = (RemoteMessage)obj;
                        if(msg.type == RemoteMessage.MessageType.CHAT) {
                            if(chatWindow != null) { chatWindow.appendChatMessage(clientName + ": " + msg.chatText); }
                        } else if(msg.type == RemoteMessage.MessageType.FILE) {
                            SwingUtilities.invokeLater(() -> {
                                int choice = JOptionPane.showConfirmDialog(hostGUI, clientName + " sent file: " + msg.fileName + "\nSave file?", "File received", JOptionPane.YES_NO_OPTION);
                                if(choice == JOptionPane.YES_OPTION) {
                                    JFileChooser fc = new JFileChooser();
                                    fc.setSelectedFile(new File(msg.fileName));
                                    if(fc.showSaveDialog(hostGUI) == JFileChooser.APPROVE_OPTION) {
                                        File saveFile = fc.getSelectedFile();
                                        try(FileOutputStream fos = new FileOutputStream(saveFile)) { fos.write(msg.fileData); } catch(IOException ex) {}
                                    }
                                }
                            });
                        }
                    }
                }
            } catch(Exception e) {} finally { stopConnection(); }
        }
        public String toString() { return clientName; }
        public void sendMouseEvent(MouseEvent e, int labelW, int labelH) {
            try {
                if(out == null) return;
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
            } catch(IOException ex) {}
        }
        public void sendKeyEvent(KeyEvent e) {
            try {
                if(out == null) return;
                RemoteEvent evt = new RemoteEvent();
                evt.type = RemoteEvent.Type.KEYBOARD;
                evt.keyID = e.getID();
                evt.keyCode = e.getKeyCode();
                evt.keyChar = e.getKeyChar();
                out.writeObject(evt);
                out.flush();
            } catch(IOException ex) {}
        }
        public void sendChatMessage(String text) {
            try {
                if(out == null) return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex) {}
        }
        public void sendFile(File file) {
            try {
                if(out == null) return;
                byte[] data = readFile(file);
                if(data == null) return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.FILE;
                msg.fileName = file.getName();
                msg.fileData = data;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex) {}
        }
        private byte[] readFile(File file) {
            try {
                byte[] fileBytes = new byte[(int)file.length()];
                try(FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(fileBytes);
                    if(read != fileBytes.length) return null;
                    return fileBytes;
                }
            } catch(IOException ex) { return null; }
        }
        public void openOrCreateChatWindow() {
            if(chatWindow == null) { chatWindow = new ChatWindow(this, clientName); }
            if(!chatWindow.isVisible()) { chatWindow.setVisible(true); }
        }
        public void stopConnection() {
            running = false;
            try { if(in != null) in.close(); } catch(IOException ex) {}
            try { if(out != null) out.close(); } catch(IOException ex) {}
            try { if(socket != null && !socket.isClosed()) socket.close(); } catch(IOException ex) {}
        }
    }
    static class ChatWindow extends JFrame {
        private javax.swing.JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private ClientHandler handler;
        private String clientName;
        public ChatWindow(ClientHandler handler, String clientName) {
            super("Chat with " + clientName);
            this.handler = handler;
            this.clientName = clientName;
            setSize(400,300);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            chatArea = new javax.swing.JTextArea();
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
                if(!text.isEmpty()) {
                    appendChatMessage("You: " + text);
                    handler.sendChatMessage(text);
                    chatInput.setText("");
                }
            });
        }
        public void appendChatMessage(String msg) { chatArea.append(msg + "\n"); }
    }
    static class RemoteDesktopClient {
        private volatile boolean running = true;
        private ClientGUI clientGUI;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private Socket socket;
        private ConnectionEntry conn;
        public void startClient(String host, int port, ConnectionEntry conn) {
            this.conn = conn;
            try {
                socket = new Socket(host, port);
                conn.setOnline(true);
                clientGUI = new ClientGUI();
                clientGUI.setController(this);
                clientGUI.setVisible(true);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                sendClientHostname();
                Thread captureThread = new Thread(() -> {
                    try {
                        Robot robot = new Robot();
                        while(running) {
                            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                            BufferedImage capture = robot.createScreenCapture(new Rectangle(screenSize));
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(capture, "jpg", baos);
                            byte[] imageBytes = baos.toByteArray();
                            synchronized(out) {
                                out.writeObject(imageBytes);
                                out.flush();
                            }
                            Thread.sleep(100);
                        }
                    } catch(Exception ex) {}
                });
                captureThread.start();
                Robot robot = new Robot();
                while(running) {
                    Object obj = in.readObject();
                    if(obj instanceof RemoteEvent) {
                        RemoteEvent evt = (RemoteEvent)obj;
                        handleRemoteEvent(evt, robot);
                    } else if(obj instanceof RemoteMessage) {
                        RemoteMessage msg = (RemoteMessage)obj;
                        if(msg.type == RemoteMessage.MessageType.CHAT) { clientGUI.appendChatMessage("Host: " + msg.chatText); }
                        else if(msg.type == RemoteMessage.MessageType.FILE) {
                            SwingUtilities.invokeLater(() -> {
                                int choice = JOptionPane.showConfirmDialog(clientGUI, "Host sent file: " + msg.fileName + "\nSave file?", "File received", JOptionPane.YES_NO_OPTION);
                                if(choice == JOptionPane.YES_OPTION) {
                                    JFileChooser fc = new JFileChooser();
                                    fc.setSelectedFile(new File(msg.fileName));
                                    if(fc.showSaveDialog(clientGUI)==JFileChooser.APPROVE_OPTION) {
                                        File saveFile = fc.getSelectedFile();
                                        try(FileOutputStream fos = new FileOutputStream(saveFile)) { fos.write(msg.fileData); } catch(IOException ex) {}
                                    }
                                }
                            });
                            clientGUI.appendChatMessage("Host sent file: " + msg.fileName);
                        }
                    }
                }
                captureThread.join();
                in.close();
                out.close();
                socket.close();
            } catch(Exception e) {
                conn.setOnline(false);
            }
        }
        private void sendClientHostname() {
            try {
                String localName;
                try { localName = InetAddress.getLocalHost().getHostName(); } catch(Exception ex) { localName = "Unknown Client"; }
                out.writeObject(localName);
                out.flush();
            } catch(IOException ex) {}
        }
        private void handleRemoteEvent(RemoteEvent evt, Robot robot) {
            if(evt.type == RemoteEvent.Type.MOUSE) {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int realX = (int)(((double)evt.x/evt.displayWidth)*screenSize.width);
                int realY = (int)(((double)evt.y/evt.displayHeight)*screenSize.height);
                robot.mouseMove(realX, realY);
                if(evt.mouseID == MouseEvent.MOUSE_PRESSED) {
                    if(evt.button == MouseEvent.BUTTON1) robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    else if(evt.button == MouseEvent.BUTTON3) robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                } else if(evt.mouseID == MouseEvent.MOUSE_RELEASED) {
                    if(evt.button == MouseEvent.BUTTON1) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    else if(evt.button == MouseEvent.BUTTON3) robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                }
            } else if(evt.type == RemoteEvent.Type.KEYBOARD) {
                if(evt.keyID == KeyEvent.KEY_PRESSED) robot.keyPress(evt.keyCode);
                else if(evt.keyID == KeyEvent.KEY_RELEASED) robot.keyRelease(evt.keyCode);
            }
        }
        public void sendChatMessage(String text) {
            try {
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex) {}
        }
        public void sendFileMessage(File file) {
            try {
                byte[] data = readFile(file);
                if(data == null) return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.FILE;
                msg.fileName = file.getName();
                msg.fileData = data;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex) {}
        }
        private byte[] readFile(File file) {
            try {
                byte[] fileBytes = new byte[(int)file.length()];
                try(FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(fileBytes);
                    if(read != fileBytes.length) return null;
                    return fileBytes;
                }
            } catch(IOException ex) { return null; }
        }
        public void stopConnection() {
            running = false;
            try { if(in != null) in.close(); } catch(IOException ex) {}
            try { if(out != null) out.close(); } catch(IOException ex) {}
            try { if(socket != null && !socket.isClosed()) socket.close(); } catch(IOException ex) {}
            SwingUtilities.invokeLater(() -> { System.exit(0); });
            conn.setOnline(false);
        }
    }
    static class ClientGUI extends JFrame {
        private javax.swing.JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private JButton sendFileButton;
        private JButton stopConnButton;
        private RemoteDesktopClient controller;
        public ClientGUI() {
            super("Client – Remote Controlled");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(600,400);
            setLayout(new BorderLayout());
            JLabel infoLabel = new JLabel("Your system is being remotely controlled.", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);
            chatArea = new javax.swing.JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScroll = new JScrollPane(chatArea);
            add(chatScroll, BorderLayout.CENTER);
            JPanel inputPanel = new JPanel(new BorderLayout());
            chatInput = new JTextField();
            sendChatButton = new JButton("Send");
            sendFileButton = new JButton("Send File");
            stopConnButton = new JButton("Stop Connection");
            JPanel btnPanel = new JPanel();
            btnPanel.add(sendChatButton);
            btnPanel.add(sendFileButton);
            btnPanel.add(stopConnButton);
            inputPanel.add(chatInput, BorderLayout.CENTER);
            inputPanel.add(btnPanel, BorderLayout.EAST);
            add(inputPanel, BorderLayout.SOUTH);
            sendChatButton.addActionListener(e -> {
                String text = chatInput.getText().trim();
                if(!text.isEmpty()){
                    appendChatMessage("You: " + text);
                    if(controller != null) { controller.sendChatMessage(text); }
                    chatInput.setText("");
                }
            });
            sendFileButton.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    final long MAX_FILE_SIZE = 10*1024*1024;
                    if(file.length() <= MAX_FILE_SIZE) {
                        appendChatMessage("You sent file: " + file.getName());
                        if(controller != null) { controller.sendFileMessage(file); }
                    } else {
                        JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit.");
                    }
                }
            });
            stopConnButton.addActionListener(e -> {
                if(controller != null) { controller.stopConnection(); }
            });
        }
        public void setController(RemoteDesktopClient c) { controller = c; }
        public void appendChatMessage(String msg) {
            SwingUtilities.invokeLater(() -> { chatArea.append(msg + "\n"); });
        }
    }
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
        public ConnectionEntry(String ip, int port) { this.ip = ip; this.port = port; this.online = false; }
        public String getIp() { return ip; }
        public int getPort() { return port; }
        public void setOnline(boolean online) { this.online = online; }
        public boolean isOnline() { return online; }
        public String toString() { return ip + ":" + port + " - " + (online ? "Online" : "Offline"); }
    }
    static class ClientLauncher extends JFrame {
        private JComboBox<ConnectionEntry> connectionCombo;
        private DefaultComboBoxModel<ConnectionEntry> comboModel;
        private JTextField portField;
        private JButton connectBtn;
        public ClientLauncher() {
            super("Client Launcher");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400,150);
            setLayout(new FlowLayout());
            comboModel = new DefaultComboBoxModel<>();
            connectionCombo = new JComboBox<>(comboModel);
            connectionCombo.setEditable(true);
            portField = new JTextField("5000", 6);
            connectBtn = new JButton("Connect");
            add(new JLabel("IP:"));
            add(connectionCombo);
            add(new JLabel("Port:"));
            add(portField);
            add(connectBtn);
            connectBtn.addActionListener((ActionEvent e) -> {
                String ip = connectionCombo.getEditor().getItem().toString().trim();
                int port = Integer.parseInt(portField.getText().trim());
                boolean exists = false;
                for(int i=0; i<comboModel.getSize(); i++){
                    ConnectionEntry entry = comboModel.getElementAt(i);
                    if(entry.getIp().equals(ip) && entry.getPort()==port){
                        exists = true;
                        break;
                    }
                }
                if(!exists){
                    ConnectionEntry newEntry = new ConnectionEntry(ip, port);
                    comboModel.addElement(newEntry);
                }
                ConnectionEntry selected = new ConnectionEntry(ip, port);
                for(int i=0; i<comboModel.getSize(); i++){
                    ConnectionEntry entry = comboModel.getElementAt(i);
                    if(entry.getIp().equals(ip) && entry.getPort()==port){ selected = entry; break; }
                }
                setVisible(false);
                RemoteDesktopClient client = new RemoteDesktopClient();
                client.startClient(ip, port, selected);
            });
        }
    }
}