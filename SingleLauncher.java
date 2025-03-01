import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.Robot;
import java.awt.datatransfer.*;
import java.util.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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
        
        // Minimal launcher: fields for host IP & port plus two buttons.
        JLabel ipLabel = new JLabel("Client Connect IP:");
        ipField = new JTextField("127.0.0.1", 10);
        JLabel portLabel = new JLabel("Port:");
        portField = new JTextField("5000", 5);
        JButton hostButton = new JButton("Start Host");
        JButton clientButton = new JButton("Start Client");
        
        gbc.gridx=0; gbc.gridy=0;
        add(ipLabel, gbc);
        gbc.gridx=1;
        add(ipField, gbc);
        gbc.gridx=0; gbc.gridy=1;
        add(portLabel, gbc);
        gbc.gridx=1;
        add(portField, gbc);
        gbc.gridx=0; gbc.gridy=2;
        add(hostButton, gbc);
        gbc.gridx=1;
        add(clientButton, gbc);
        
        // Actions:
        hostButton.addActionListener(e -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                HostGUI hostGUI = new HostGUI();
                // Optionally maximize the host window
                hostGUI.setExtendedState(JFrame.MAXIMIZED_BOTH);
                hostGUI.setVisible(true);
            });
        });
        
        clientButton.addActionListener(e -> {
            setVisible(false);
            String hostIP = ipField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            new Thread(() -> {
                RemoteDesktopClient client = new RemoteDesktopClient();
                client.startClient(hostIP, port);
            }).start();
        });
    }
    
    // ──────────────────────────────────────────────────────
    // HOST SIDE CLASSES
    // ──────────────────────────────────────────────────────
    
    // HostGUI: shows list of clients on the left, remote screen on the center,
    // and a chat panel at the bottom to exchange chat messages/files with the selected client.
    static class HostGUI extends JFrame {
        private DefaultListModel<ClientHandler> clientListModel;
        private JList<ClientHandler> clientJList;
        private JLabel screenLabel; 
        // Chat components:
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private JButton sendFileButton;
        
        private ServerThread serverThread;
        
        public HostGUI() {
            super("Host – Remote Desktop");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1000, 700);
            setLayout(new BorderLayout());
            
            // Left panel: List of connected clients
            clientListModel = new DefaultListModel<>();
            clientJList = new JList<>(clientListModel);
            clientJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            clientJList.addListSelectionListener(new ListSelectionListener(){
                public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        // When switching clients, clear chat area.
                        chatArea.setText("");
                        ClientHandler selected = clientJList.getSelectedValue();
                        if (selected != null) {
                            // Show the last received screenshot from that client
                            updateClientScreen(selected, selected.getLastReceivedImage());
                        }
                    }
                }
            });
            JScrollPane listScroll = new JScrollPane(clientJList);
            listScroll.setPreferredSize(new Dimension(200, getHeight()));
            add(listScroll, BorderLayout.WEST);
            
            // Center panel: Remote screen display (scaled image)
            screenLabel = new JLabel();
            screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
            screenLabel.setVerticalAlignment(SwingConstants.CENTER);
            screenLabel.setBackground(Color.BLACK);
            screenLabel.setOpaque(true);
            // Forward mouse/keyboard events:
            MouseAdapter mouseAdapter = new MouseAdapter(){
                public void mousePressed(MouseEvent e){ forwardMouseEvent(e); }
                public void mouseReleased(MouseEvent e){ forwardMouseEvent(e); }
                public void mouseMoved(MouseEvent e){ forwardMouseEvent(e); }
                public void mouseDragged(MouseEvent e){ forwardMouseEvent(e); }
            };
            screenLabel.addMouseListener(mouseAdapter);
            screenLabel.addMouseMotionListener(mouseAdapter);
            screenLabel.addKeyListener(new KeyAdapter(){
                public void keyPressed(KeyEvent e){ forwardKeyEvent(e); }
                public void keyReleased(KeyEvent e){ forwardKeyEvent(e); }
            });
            
            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.add(screenLabel, BorderLayout.CENTER);
            add(centerPanel, BorderLayout.CENTER);
            
            // South panel: Chat area & File Send
            JPanel chatPanel = new JPanel(new BorderLayout());
            chatArea = new JTextArea(5, 30);
            chatArea.setEditable(false);
            JScrollPane chatScroll = new JScrollPane(chatArea);
            chatInput = new JTextField();
            sendChatButton = new JButton("Send");
            sendFileButton = new JButton("Send File");
            
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(chatInput, BorderLayout.CENTER);
            JPanel btnPanel = new JPanel();
            btnPanel.add(sendChatButton);
            btnPanel.add(sendFileButton);
            inputPanel.add(btnPanel, BorderLayout.EAST);
            
            chatPanel.add(chatScroll, BorderLayout.CENTER);
            chatPanel.add(inputPanel, BorderLayout.SOUTH);
            
            add(chatPanel, BorderLayout.SOUTH);
            
            // Send chat action:
            sendChatButton.addActionListener(e -> {
                ClientHandler selected = clientJList.getSelectedValue();
                if (selected != null) {
                    String text = chatInput.getText().trim();
                    if (!text.isEmpty()) {
                        // Append our message locally:
                        appendChatMessage(selected, "Host: " + text);
                        selected.sendChatMessage(text);
                        chatInput.setText("");
                    }
                }
            });
            
            // Send file action:
            sendFileButton.addActionListener(e -> {
                ClientHandler selected = clientJList.getSelectedValue();
                if (selected != null) {
                    JFileChooser fc = new JFileChooser();
                    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
                        // Limit file to 10 MB:
                        final long MAX_FILE_SIZE = 10 * 1024 * 1024;
                        if (file.length() <= MAX_FILE_SIZE) {
                            selected.sendFile(file);
                            appendChatMessage(selected, "Host sent file: " + file.getName());
                        } else {
                            JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit.");
                        }
                    }
                }
            });
            
            // Start a server thread on port 5000
            serverThread = new ServerThread(5000, this);
            serverThread.start();
        }
        
        // Called by ClientHandler when a new screenshot arrives.
        public void updateClientScreen(ClientHandler handler, ImageIcon icon) {
            if (clientJList.getSelectedValue() != handler || icon == null) return;
            int w = screenLabel.getWidth();
            int h = screenLabel.getHeight();
            if (w > 0 && h > 0) {
                Image scaled = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
                screenLabel.setIcon(new ImageIcon(scaled));
            } else {
                screenLabel.setIcon(icon);
            }
        }
        
        // Append a chat message in the chat area.
        public void appendChatMessage(ClientHandler handler, String message) {
            // Only show message if this client is selected.
            if (clientJList.getSelectedValue() == handler) {
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(message + "\n");
                });
            }
        }
        
        private void forwardMouseEvent(MouseEvent e) {
            ClientHandler selected = clientJList.getSelectedValue();
            if (selected != null) {
                selected.sendMouseEvent(e, screenLabel.getWidth(), screenLabel.getHeight());
            }
        }
        
        private void forwardKeyEvent(KeyEvent e) {
            ClientHandler selected = clientJList.getSelectedValue();
            if (selected != null) {
                selected.sendKeyEvent(e);
            }
        }
    }
    
    // ServerThread: continually accepts new client connections.
    static class ServerThread extends Thread {
        private int port;
        private HostGUI hostGUI;
        
        public ServerThread(int port, HostGUI hostGUI) {
            this.port = port;
            this.hostGUI = hostGUI;
        }
        
        public void run() {
            try (ServerSocket ss = new ServerSocket(port)) {
                while(true) {
                    Socket clientSocket = ss.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, hostGUI);
                    handler.start();
                    hostGUI.clientListModel.addElement(handler);
                }
            } catch (IOException e) {
                // silent
            }
        }
    }
    
    // ClientHandler: one thread per remote client.
    static class ClientHandler extends Thread {
        private Socket socket;
        private HostGUI hostGUI;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private ImageIcon lastReceivedImage;
        private String clientName = "Unknown Client";
        
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
                
                // First message: client's hostname.
                Object first = in.readObject();
                if (first instanceof String) {
                    clientName = (String) first;
                }
                
                // Continuously read incoming objects…
                while(true) {
                    Object obj = in.readObject();
                    if (obj instanceof byte[]) {
                        // This is a screenshot from client.
                        lastReceivedImage = new ImageIcon((byte[])obj);
                        hostGUI.updateClientScreen(this, lastReceivedImage);
                    } else if (obj instanceof RemoteMessage) {
                        RemoteMessage msg = (RemoteMessage) obj;
                        if (msg.type == RemoteMessage.MessageType.CHAT) {
                            hostGUI.appendChatMessage(this, clientName + ": " + msg.chatText);
                        } else if (msg.type == RemoteMessage.MessageType.FILE) {
                            // For file messages, ask where to save the file.
                            SwingUtilities.invokeLater(() -> {
                                int choice = JOptionPane.showConfirmDialog(hostGUI,
                                    clientName + " sent file: " + msg.fileName + "\nSave file?",
                                    "File received", JOptionPane.YES_NO_OPTION);
                                if(choice == JOptionPane.YES_OPTION) {
                                    JFileChooser fc = new JFileChooser();
                                    fc.setSelectedFile(new File(msg.fileName));
                                    if(fc.showSaveDialog(hostGUI)==JFileChooser.APPROVE_OPTION) {
                                        File saveFile = fc.getSelectedFile();
                                        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                                            fos.write(msg.fileData);
                                        } catch (IOException ex) {
                                            // silent
                                        }
                                    }
                                }
                            });
                            hostGUI.appendChatMessage(this, clientName + " sent file: " + msg.fileName);
                        }
                    }
                }
            } catch (Exception e) {
                // silent
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
        
        // When shown in the JList, display the client’s hostname.
        public String toString() {
            return clientName;
        }
        
        // Send RemoteEvent (for mouse/keyboard)
        public void sendMouseEvent(MouseEvent e, int labelW, int labelH) {
            try {
                if(out==null)return;
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
                if(out==null)return;
                RemoteEvent evt = new RemoteEvent();
                evt.type = RemoteEvent.Type.KEYBOARD;
                evt.keyID = e.getID();
                evt.keyCode = e.getKeyCode();
                evt.keyChar = e.getKeyChar();
                out.writeObject(evt);
                out.flush();
            } catch(IOException ex){ }
        }
        
        // Send a chat message to the client.
        public void sendChatMessage(String text) {
            try {
                if(out==null)return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex){ }
        }
        
        // Send a file to the client.
        public void sendFile(File file) {
            try {
                if(out==null)return;
                byte[] data = readFile(file);
                if(data==null)return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.FILE;
                msg.fileName = file.getName();
                msg.fileData = data;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex){ }
        }
        private byte[] readFile(File file) {
            try {
                byte[] fileBytes = new byte[(int)file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(fileBytes);
                    if(read != fileBytes.length) return null;
                    return fileBytes;
                }
            } catch(IOException ex){
                return null;
            }
        }
    }
    
    // ──────────────────────────────────────────────────────
    // CLIENT SIDE CLASSES
    // ──────────────────────────────────────────────────────
    
    // RemoteDesktopClient – now creates its own client GUI with a chat panel and file–send option.
    static class RemoteDesktopClient {
        private boolean running = true;
        private ClientGUI clientGUI;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        
        public void startClient(String host, int port) {
            try {
                Socket socket = new Socket(host, port);
                // Create the client GUI (shows chat area at bottom)
                clientGUI = new ClientGUI();
                clientGUI.setVisible(true);
                
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                
                // Send our hostname as first message.
                sendClientHostname();
                
                // Start screenshot capture thread: (client sends its screenshot to host)
                Thread captureThread = new Thread(() -> {
                    try {
                        Robot robot = new Robot();
                        while (running) {
                            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                            BufferedImage capture = robot.createScreenCapture(new Rectangle(screenSize));
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(capture, "jpg", baos);
                            byte[] imageBytes = baos.toByteArray();
                            synchronized(out) {
                                out.writeObject(imageBytes);
                                out.flush();
                            }
                            Thread.sleep(100); // ~10 fps
                        }
                    } catch(Exception ex){ }
                });
                captureThread.start();
                
                // Main loop: process incoming remote control events and chat/file messages.
                Robot robot = new Robot();
                while(running){
                    Object obj = in.readObject();
                    if(obj instanceof RemoteEvent) {
                        RemoteEvent evt = (RemoteEvent)obj;
                        handleRemoteEvent(evt, robot);
                    } else if(obj instanceof RemoteMessage) {
                        RemoteMessage msg = (RemoteMessage)obj;
                        if(msg.type == RemoteMessage.MessageType.CHAT) {
                            clientGUI.appendChatMessage("Host: " + msg.chatText);
                        } else if(msg.type == RemoteMessage.MessageType.FILE) {
                            // Ask user where to save the received file.
                            SwingUtilities.invokeLater(() -> {
                                int choice = JOptionPane.showConfirmDialog(clientGUI,
                                    "Host sent file: " + msg.fileName + "\nSave file?",
                                    "File received", JOptionPane.YES_NO_OPTION);
                                if(choice==JOptionPane.YES_OPTION) {
                                    JFileChooser fc = new JFileChooser();
                                    fc.setSelectedFile(new File(msg.fileName));
                                    if(fc.showSaveDialog(clientGUI)==JFileChooser.APPROVE_OPTION) {
                                        File saveFile = fc.getSelectedFile();
                                        try (FileOutputStream fos = new FileOutputStream(saveFile)){
                                            fos.write(msg.fileData);
                                        } catch(IOException ex){ }
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
                // silent
            }
        }
        
        private void sendClientHostname() {
            try {
                String localName;
                try {
                    localName = InetAddress.getLocalHost().getHostName();
                } catch(Exception ex) { localName = "Unknown Client"; }
                out.writeObject(localName);
                out.flush();
            } catch(IOException ex){ }
        }
        
        private void handleRemoteEvent(RemoteEvent evt, Robot robot) {
            // The client is being controlled by the host.
            // Map coordinates from the (host's transmitted) display dimensions to the local (real) screen.
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int realX = (int)(((double)evt.x / evt.displayWidth) * screenSize.width);
            int realY = (int)(((double)evt.y / evt.displayHeight) * screenSize.height);
            robot.mouseMove(realX, realY);
            if(evt.mouseID == MouseEvent.MOUSE_PRESSED) {
                if(evt.button == MouseEvent.BUTTON1)
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                else if(evt.button == MouseEvent.BUTTON3)
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            } else if(evt.mouseID == MouseEvent.MOUSE_RELEASED) {
                if(evt.button == MouseEvent.BUTTON1)
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                else if(evt.button == MouseEvent.BUTTON3)
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            }
            if(evt.type == RemoteEvent.Type.KEYBOARD) {
                if(evt.keyID == KeyEvent.KEY_PRESSED)
                    robot.keyPress(evt.keyCode);
                else if(evt.keyID == KeyEvent.KEY_RELEASED)
                    robot.keyRelease(evt.keyCode);
            }
        }
        
        // Allow the client GUI to send a chat message/file.
        public void sendChatMessage(String text) {
            try {
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex){ }
        }
        public void sendFileMessage(File file) {
            try {
                byte[] data = readFile(file);
                if(data==null)return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.FILE;
                msg.fileName = file.getName();
                msg.fileData = data;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex){ }
        }
        private byte[] readFile(File file) {
            try {
                byte[] fileBytes = new byte[(int)file.length()];
                try(FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(fileBytes);
                    if(read!=fileBytes.length)return null;
                    return fileBytes;
                }
            } catch(IOException ex){ return null; }
        }
    }
    
    // ClientGUI: the client’s UI – a minimal window with a chat panel and a “Send File” button.
    static class ClientGUI extends JFrame {
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private JButton sendFileButton;
        
        private RemoteDesktopClient clientController;
        
        public ClientGUI() {
            super("Client – Remote Controlled");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(600,400);
            setLayout(new BorderLayout());
            
            // Upper area: a label indicates that your system is controlled.
            JLabel infoLabel = new JLabel("Your system is being remotely controlled.", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);
            
            // Center: chat area.
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScroll = new JScrollPane(chatArea);
            add(chatScroll, BorderLayout.CENTER);
            
            // South: chat input panel.
            JPanel inputPanel = new JPanel(new BorderLayout());
            chatInput = new JTextField();
            sendChatButton = new JButton("Send");
            sendFileButton = new JButton("Send File");
            JPanel btnPanel = new JPanel();
            btnPanel.add(sendChatButton);
            btnPanel.add(sendFileButton);
            inputPanel.add(chatInput, BorderLayout.CENTER);
            inputPanel.add(btnPanel, BorderLayout.EAST);
            add(inputPanel, BorderLayout.SOUTH);
            
            // Actions:
            sendChatButton.addActionListener(e -> {
                String text = chatInput.getText().trim();
                if(!text.isEmpty()){
                    appendChatMessage("You: " + text);
                    // Use the controller to send the chat message.
                    if(controller != null) {
                        controller.sendChatMessage(text);
                    }
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
                        if(controller != null) {
                            controller.sendFileMessage(file);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit.");
                    }
                }
            });
        }
        
        // A pointer set by RemoteDesktopClient:
        private RemoteDesktopClient controller;
        public void setController(RemoteDesktopClient c) {
            controller = c;
        }
        
        public void appendChatMessage(String msg) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append(msg + "\n");
            });
        }
    }
    
    // ──────────────────────────────────────────────────────
    // SHARED MESSAGE CLASSES
    // ──────────────────────────────────────────────────────
    
    // RemoteEvent: used for sending input events
    static class RemoteEvent implements Serializable {
        enum Type { MOUSE, KEYBOARD }
        public Type type;
        // For mouse:
        public int mouseID;       // e.g., MouseEvent.MOUSE_PRESSED, etc.
        public int button;
        public int x, y;
        public int displayWidth, displayHeight;
        // For keyboard:
        public int keyID;
        public int keyCode;
        public char keyChar;
    }
    
    // RemoteMessage: used for chat and file transfers.
    static class RemoteMessage implements Serializable {
        enum MessageType { CHAT, FILE }
        public MessageType type;
        // For chat:
        public String chatText;
        // For file:
        public String fileName;
        public byte[] fileData;
    }
}
