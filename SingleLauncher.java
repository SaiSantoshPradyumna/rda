import java.awt.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
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
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
        hostButton.addActionListener(e -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                int port = Integer.parseInt(portField.getText().trim());
                HostGUI hostGUI = new HostGUI(port);
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

    static class HostGUI extends JFrame {
        private DefaultListModel<ClientHandler> clientListModel;
        private JList<ClientHandler> clientJList;
        private JLabel screenLabel;
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
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
            } catch (UnknownHostException ex) {}
            JLabel hostIpLabel = new JLabel("Hosting on IP: " + localIp + "   Port: " + listeningPort, SwingConstants.CENTER);
            add(hostIpLabel, BorderLayout.NORTH);
            clientListModel = new DefaultListModel<>();
            clientJList = new JList<>(clientListModel);
            clientJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            clientJList.addListSelectionListener(new ListSelectionListener(){
                public void valueChanged(ListSelectionEvent e) {
                    if (!e.getValueIsAdjusting()) {
                        chatArea.setText("");
                        ClientHandler selected = clientJList.getSelectedValue();
                        if (selected != null) {
                            updateClientScreen(selected, selected.getLastReceivedImage());
                        }
                    }
                }
            });
            JScrollPane listScroll = new JScrollPane(clientJList);
            listScroll.setPreferredSize(new Dimension(200, getHeight()));
            add(listScroll, BorderLayout.WEST);
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
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && screenLabel.isDisplayable()) {
                    SwingUtilities.invokeLater(() -> screenLabel.requestFocusInWindow());
                }
            });
            screenLabel.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    forwardKeyEvent(e);
                }
                @Override
                public void keyReleased(KeyEvent e) {
                    forwardKeyEvent(e);
                }
                @Override
                public void keyTyped(KeyEvent e) {
                    forwardKeyEvent(e);
                }
            });
            screenLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    screenLabel.requestFocusInWindow();
                }
            });
            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.add(screenLabel, BorderLayout.CENTER);
            add(centerPanel, BorderLayout.CENTER);
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
            sendChatButton.addActionListener(e -> {
                ClientHandler selected = clientJList.getSelectedValue();
                if (selected != null) {
                    String text = chatInput.getText().trim();
                    if (!text.isEmpty()) {
                        appendChatMessage(selected, "Host: " + text);
                        selected.sendChatMessage(text);
                        chatInput.setText("");
                    }
                }
            });
            sendFileButton.addActionListener(e -> {
                ClientHandler selected = clientJList.getSelectedValue();
                if (selected != null) {
                    JFileChooser fc = new JFileChooser();
                    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File file = fc.getSelectedFile();
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
            serverThread = new ServerThread(listeningPort, this);
            serverThread.start();
        }

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

        public void appendChatMessage(ClientHandler handler, String message) {
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
            } catch (IOException e) {}
        }
    }

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
                Object first = in.readObject();
                if (first instanceof String) {
                    clientName = (String) first;
                }
                while(true) {
                    Object obj = in.readObject();
                    if (obj instanceof byte[]) {
                        lastReceivedImage = new ImageIcon((byte[])obj);
                        hostGUI.updateClientScreen(this, lastReceivedImage);
                    } else if (obj instanceof RemoteMessage) {
                        RemoteMessage msg = (RemoteMessage) obj;
                        if (msg.type == RemoteMessage.MessageType.CHAT) {
                            hostGUI.appendChatMessage(this, clientName + ": " + msg.chatText);
                        } else if (msg.type == RemoteMessage.MessageType.FILE) {
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
                                        } catch (IOException ex) {}
                                    }
                                }
                            });
                            hostGUI.appendChatMessage(this, clientName + " sent file: " + msg.fileName);
                        }
                    }
                }
            } catch (Exception e) {
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        public String toString() {
            return clientName;
        }

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
            } catch (IOException ex) {}
        }

        public void sendKeyEvent(KeyEvent e) {
            try {
                if (out == null) return;
                RemoteEvent evt = new RemoteEvent();
                evt.type = RemoteEvent.Type.KEYBOARD;
                evt.keyID = e.getID();
                evt.keyCode = e.getKeyCode();
                evt.keyChar = e.getKeyChar();
                out.writeObject(evt);
                out.flush();
            } catch(IOException ex){}
        }

        public void sendChatMessage(String text) {
            try {
                if(out==null)return;
                RemoteMessage msg = new RemoteMessage();
                msg.type = RemoteMessage.MessageType.CHAT;
                msg.chatText = text;
                out.writeObject(msg);
                out.flush();
            } catch(IOException ex){}
        }

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
            } catch(IOException ex){}
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

    static class RemoteDesktopClient {
        private boolean running = true;
        private ClientGUI clientGUI;
        private ObjectOutputStream out;
        private ObjectInputStream in;

        public void startClient(String host, int port) {
            try {
                Socket socket = new Socket(host, port);
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
                            Thread.sleep(100);
                        }
                    } catch(Exception ex){}
                });
                captureThread.start();
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
                                        } catch(IOException ex){}
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
            } catch(Exception e) {}
        }

        private void sendClientHostname() {
            try {
                String localName;
                try {
                    localName = InetAddress.getLocalHost().getHostName();
                } catch(Exception ex) { localName = "Unknown Client"; }
                out.writeObject(localName);
                out.flush();
            } catch(IOException ex){}
        }

        private void handleRemoteEvent(RemoteEvent evt, Robot robot) {
            if (evt.type == RemoteEvent.Type.MOUSE) {
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
            }
            else if (evt.type == RemoteEvent.Type.KEYBOARD) {
                if(evt.keyID == KeyEvent.KEY_PRESSED) {
                    robot.keyPress(evt.keyCode);
                } else if(evt.keyID == KeyEvent.KEY_RELEASED) {
                    robot.keyRelease(evt.keyCode);
                } else if(evt.keyID == KeyEvent.KEY_TYPED) {
                    int code = KeyEvent.getExtendedKeyCodeForChar(evt.keyChar);
                    if (code != KeyEvent.VK_UNDEFINED) {
                        robot.keyPress(code);
                        robot.keyRelease(code);
                    }
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
            } catch(IOException ex){}
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
            } catch(IOException ex){}
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

    static class ClientGUI extends JFrame {
        private JTextArea chatArea;
        private JTextField chatInput;
        private JButton sendChatButton;
        private JButton sendFileButton;
        private RemoteDesktopClient controller;

        public ClientGUI() {
            super("Client – Remote Controlled");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(600,400);
            setLayout(new BorderLayout());
            JLabel infoLabel = new JLabel("Your system is being remotely controlled.", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane chatScroll = new JScrollPane(chatArea);
            add(chatScroll, BorderLayout.CENTER);
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
            sendChatButton.addActionListener(e -> {
                String text = chatInput.getText().trim();
                if(!text.isEmpty()){
                    appendChatMessage("You: " + text);
                    if(controller != null) {
                        controller.sendChatMessage(text);
                    }
                    chatInput.setText("");
                }
            });
            sendFileButton.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if(fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
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

        public void setController(RemoteDesktopClient c) {
            controller = c;
        }

        public void appendChatMessage(String msg) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append(msg + "\n");
            });
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
}