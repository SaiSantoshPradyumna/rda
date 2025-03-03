import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxEditor;

public class SingleLauncher extends JFrame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SingleLauncher l = new SingleLauncher();
            l.setVisible(true);
        });
    }
    public SingleLauncher() {
        super("Remote Desktop Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(320, 150);
        setLayout(new FlowLayout());
        JButton host = new JButton("Start Host"), client = new JButton("Start Client");
        add(host);
        add(client);
        host.addActionListener(e -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> {
                HostGUI h = new HostGUI(5000);
                h.setExtendedState(JFrame.MAXIMIZED_BOTH);
                h.setVisible(true);
            });
        });
        client.addActionListener(e -> {
            setVisible(false);
            SwingUtilities.invokeLater(() -> new ClientLauncher().setVisible(true));
        });
    }
    static class HostGUI extends JFrame {
        private JComboBox<ClientHandler> combo;
        private JLabel screenLabel;
        private ServerThread serverThread;
        public HostGUI(int p) {
            super("Host – Remote Desktop");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1000, 700);
            setLayout(new BorderLayout());
            String ip = "Unknown";
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception x) {}
            JLabel hl = new JLabel("Hosting on IP: " + ip + "   Port: " + p, SwingConstants.CENTER);
            JPanel top = new JPanel(new FlowLayout());
            JLabel c = new JLabel("Connections:");
            combo = new JComboBox<>();
            JButton chat = new JButton("Open Chat"), stop = new JButton("Stop Connection"), send = new JButton("Send File");
            chat.addActionListener(e -> {
                ClientHandler sel = (ClientHandler) combo.getSelectedItem();
                if (sel != null) sel.openOrCreateChatWindow();
            });
            stop.addActionListener(e -> {
                ClientHandler sel = (ClientHandler) combo.getSelectedItem();
                if (sel != null) sel.stopConnection();
            });
            send.addActionListener(e -> {
                ClientHandler s = (ClientHandler) combo.getSelectedItem();
                if (s != null) {
                    JFileChooser fc = new JFileChooser();
                    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                        File f = fc.getSelectedFile();
                        if (f.length() <= 10 * 1024 * 1024) s.sendFile(f);
                        else JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit.");
                    }
                }
            });
            top.add(c);
            top.add(combo);
            top.add(chat);
            top.add(stop);
            top.add(send);
            JPanel north = new JPanel(new BorderLayout());
            north.add(hl, BorderLayout.NORTH);
            north.add(top, BorderLayout.SOUTH);
            add(north, BorderLayout.NORTH);
            screenLabel = new JLabel();
            screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
            screenLabel.setOpaque(true);
            screenLabel.setBackground(Color.BLACK);
            screenLabel.setForeground(Color.WHITE);
            screenLabel.setText("Waiting for client connection...");
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { forward(e); }
                public void mouseReleased(MouseEvent e) { forward(e); }
                public void mouseMoved(MouseEvent e) { forward(e); }
                public void mouseDragged(MouseEvent e) { forward(e); }
            };
            screenLabel.addMouseListener(ma);
            screenLabel.addMouseMotionListener(ma);
            screenLabel.setFocusable(true);
            screenLabel.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && screenLabel.isDisplayable()) {
                    SwingUtilities.invokeLater(() -> screenLabel.requestFocusInWindow());
                }
            });
            screenLabel.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) { forwardKey(e); }
                public void keyReleased(KeyEvent e) { forwardKey(e); }
                public void keyTyped(KeyEvent e) { forwardKey(e); }
            });
            screenLabel.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    screenLabel.requestFocusInWindow();
                }
            });
            add(screenLabel, BorderLayout.CENTER);
            serverThread = new ServerThread(p, this);
            serverThread.start();
        }
        public void updateClientScreen(ClientHandler h, ImageIcon i) {
            ClientHandler s = (ClientHandler) combo.getSelectedItem();
            if (s != h || i == null) return;
            int w = screenLabel.getWidth(), ht = screenLabel.getHeight();
            if (w > 0 && ht > 0) screenLabel.setIcon(new ImageIcon(i.getImage().getScaledInstance(w, ht, Image.SCALE_SMOOTH)));
            else screenLabel.setIcon(i);
        }
        public void addClientHandler(ClientHandler h) {
            SwingUtilities.invokeLater(() -> {
                combo.addItem(h);
                if (combo.getItemCount() == 1) screenLabel.setText("");
            });
        }
        public void removeClientHandler(ClientHandler h) {
            SwingUtilities.invokeLater(() -> {
                combo.removeItem(h);
                if (combo.getItemCount() == 0) {
                    screenLabel.setIcon(null);
                    screenLabel.setText("Waiting for client connection...");
                }
            });
        }
        private void forward(MouseEvent e) {
            ClientHandler s = (ClientHandler) combo.getSelectedItem();
            if (s != null) s.sendMouseEvent(e, screenLabel.getWidth(), screenLabel.getHeight());
        }
        private void forwardKey(KeyEvent e) {
            ClientHandler s = (ClientHandler) combo.getSelectedItem();
            if (s != null) s.sendKeyEvent(e);
        }
    }
    static class ServerThread extends Thread {
        private int port;
        private HostGUI host;
        public ServerThread(int p, HostGUI h) {
            port = p;
            host = h;
        }
        public void run() {
            try (ServerSocket ss = new ServerSocket(port)) {
                while (true) {
                    Socket c = ss.accept();
                    ClientHandler ch = new ClientHandler(c, host);
                    ch.start();
                    host.addClientHandler(ch);
                }
            } catch (IOException x) {}
        }
    }
    static class ClientHandler extends Thread {
        private Socket s;
        private HostGUI gui;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private ImageIcon last;
        private String name = "Unknown Client";
        private ChatWindow chat;
        private volatile boolean running = true;
        public ClientHandler(Socket so, HostGUI g) {
            s = so;
            gui = g;
        }
        public void run() {
            try {
                out = new ObjectOutputStream(s.getOutputStream());
                out.flush();
                in = new ObjectInputStream(s.getInputStream());
                String hn;
                try {
                    hn = InetAddress.getLocalHost().getHostName();
                } catch (Exception x) {
                    hn = "Unknown Host";
                }
                out.writeObject(hn);
                out.flush();
                Object first = in.readObject();
                if (first instanceof String) name = (String) first;
                while (running) {
                    Object o = in.readObject();
                    if (o instanceof byte[]) {
                        last = new ImageIcon((byte[]) o);
                        gui.updateClientScreen(this, last);
                    } else if (o instanceof RemoteMessage) {
                        RemoteMessage m = (RemoteMessage) o;
                        if (m.type == RemoteMessage.MessageType.CHAT) {
                            if (chat != null) chat.appendChatMessage(name + ": " + m.chatText);
                        } else if (m.type == RemoteMessage.MessageType.FILE) {
                            SwingUtilities.invokeLater(() -> {
                                int c = JOptionPane.showConfirmDialog(gui, name + " sent file: " + m.fileName + "\nSave file?", "File received", JOptionPane.YES_NO_OPTION);
                                if (c == JOptionPane.YES_OPTION) {
                                    JFileChooser fc = new JFileChooser();
                                    fc.setSelectedFile(new File(m.fileName));
                                    if (fc.showSaveDialog(gui) == JFileChooser.APPROVE_OPTION) {
                                        File sf = fc.getSelectedFile();
                                        try (FileOutputStream fos = new FileOutputStream(sf)) {
                                            fos.write(m.fileData);
                                        } catch (IOException ex) {}
                                    }
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {}
            finally {
                stopConnection();
            }
        }
        public String toString() {
            return name;
        }
        public void sendMouseEvent(MouseEvent e, int w, int h) {
            try {
                if (out == null) return;
                RemoteEvent evt = new RemoteEvent();
                evt.type = RemoteEvent.Type.MOUSE;
                evt.mouseID = e.getID();
                evt.button = e.getButton();
                evt.x = e.getX();
                evt.y = e.getY();
                evt.displayWidth = w;
                evt.displayHeight = h;
                out.writeObject(evt);
                out.flush();
            } catch (IOException x) {}
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
            } catch (IOException x) {}
        }
        public void sendChatMessage(String t) {
            try {
                if (out == null) return;
                RemoteMessage m = new RemoteMessage();
                m.type = RemoteMessage.MessageType.CHAT;
                m.chatText = t;
                out.writeObject(m);
                out.flush();
            } catch (IOException x) {}
        }
        public void sendFile(File f) {
            try {
                if (out == null) return;
                byte[] d = readFile(f);
                if (d == null) return;
                RemoteMessage m = new RemoteMessage();
                m.type = RemoteMessage.MessageType.FILE;
                m.fileName = f.getName();
                m.fileData = d;
                out.writeObject(m);
                out.flush();
            } catch (IOException x) {}
        }
        private byte[] readFile(File f) {
            try {
                byte[] b = new byte[(int) f.length()];
                try (FileInputStream fis = new FileInputStream(f)) {
                    int r = fis.read(b);
                    if (r != b.length) return null;
                }
                return b;
            } catch (IOException x) {
                return null;
            }
        }
        public void openOrCreateChatWindow() {
            if (chat == null) chat = new ChatWindow(this, name);
            if (!chat.isVisible()) chat.setVisible(true);
        }
        public void stopConnection() {
            running = false;
            try {
                if (in != null) in.close();
            } catch (IOException x) {}
            try {
                if (out != null) out.close();
            } catch (IOException x) {}
            try {
                if (s != null && !s.isClosed()) s.close();
            } catch (IOException x) {}
            SwingUtilities.invokeLater(() -> gui.removeClientHandler(this));
        }
    }
    static class ChatWindow extends JFrame {
        private JTextArea area;
        private JTextField input;
        private ClientHandler handler;
        public ChatWindow(ClientHandler h, String name) {
            super("Chat with " + name);
            handler = h;
            setSize(400, 300);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            area = new JTextArea();
            area.setEditable(false);
            add(new JScrollPane(area), BorderLayout.CENTER);
            input = new JTextField();
            JButton send = new JButton("Send");
            JPanel p = new JPanel(new BorderLayout());
            p.add(input, BorderLayout.CENTER);
            p.add(send, BorderLayout.EAST);
            add(p, BorderLayout.SOUTH);
            send.addActionListener(e -> {
                String t = input.getText().trim();
                if (!t.isEmpty()) {
                    appendChatMessage("You: " + t);
                    handler.sendChatMessage(t);
                    input.setText("");
                }
            });
        }
        public void appendChatMessage(String m) {
            area.append(m + "\n");
        }
    }
    static class RemoteDesktopClient {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private ClientGUI clientGUI;
        private ConnectionEntry conn;
        private boolean running = false;
        private ClientLauncher launcher;
        private ClientChatWindow chatWindow;
        private Robot robot;
        public void startClient(String host, int port, ConnectionEntry ce, ClientLauncher la) {
            conn = ce;
            launcher = la;
            try {
                socket = new Socket(host, port);
                conn.setOnline(true);
                clientGUI = new ClientGUI();
                clientGUI.setController(this);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                String h = (String) in.readObject();
                clientGUI.setControlledBy(h);
                sendClientHostname();
                robot = new Robot();
                SwingUtilities.invokeLater(() -> {
                    clientGUI.setVisible(true);
                    clientGUI.setExtendedState(JFrame.NORMAL);
                    clientGUI.toFront();
                    clientGUI.requestFocus();
                    openChatWindow();
                });
                running = true;
                Thread receiver = new Thread(() -> {
                    try {
                        while (running) {
                            Object o = in.readObject();
                            if (o instanceof RemoteMessage) {
                                RemoteMessage m = (RemoteMessage) o;
                                if (m.type == RemoteMessage.MessageType.CHAT) {
                                    if (chatWindow != null)
                                        SwingUtilities.invokeLater(() -> chatWindow.appendChatMessage("Host: " + m.chatText));
                                } else if (m.type == RemoteMessage.MessageType.FILE) {
                                    SwingUtilities.invokeLater(() -> {
                                        int c = JOptionPane.showConfirmDialog(clientGUI, "File: " + m.fileName, "File", JOptionPane.YES_NO_OPTION);
                                        if (c == JOptionPane.YES_OPTION) {
                                            JFileChooser fc = new JFileChooser();
                                            fc.setSelectedFile(new File(m.fileName));
                                            if (fc.showSaveDialog(clientGUI) == JFileChooser.APPROVE_OPTION) {
                                                File sf = fc.getSelectedFile();
                                                try (FileOutputStream f = new FileOutputStream(sf)) {
                                                    f.write(m.fileData);
                                                } catch (IOException x) {}
                                            }
                                        }
                                    });
                                }
                            } else if (o instanceof RemoteEvent) {
                                RemoteEvent evt = (RemoteEvent) o;
                                handleRemoteEvent(evt);
                            }
                        }
                    } catch (Exception e) {} finally {
                        stopConnection();
                    }
                });
                receiver.start();
                Thread screenSender = new Thread(() -> {
                    try {
                        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                        while (running) {
                            BufferedImage capture = new Robot().createScreenCapture(screenRect);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(capture, "jpg", baos);
                            byte[] bytes = baos.toByteArray();
                            out.writeObject(bytes);
                            out.flush();
                            Thread.sleep(500);
                        }
                    } catch (Exception ex) {}
                });
                screenSender.start();
            } catch (Exception ex) {
                conn.setOnline(false);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(launcher, "Could not connect: " + host + ":" + port, "Connection Error", JOptionPane.ERROR_MESSAGE);
                    launcher.setVisible(true);
                });
            }
        }
        public void stopConnection() {
            running = false;
            try {
                if (in != null) in.close();
            } catch (IOException x) {}
            try {
                if (out != null) out.close();
            } catch (IOException x) {}
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException x) {}
            SwingUtilities.invokeLater(() -> {
                if (clientGUI != null) {
                    clientGUI.showConnectionStopped();
                    clientGUI.dispose();
                }
                if (launcher != null) launcher.setVisible(true);
                else new ClientLauncher().setVisible(true);
            });
            conn.setOnline(false);
        }
        private void sendClientHostname() {
            try {
                out.writeObject(InetAddress.getLocalHost().getHostName());
                out.flush();
            } catch (IOException x) {}
        }
        public void sendChatMessage(String t) {
            try {
                RemoteMessage m = new RemoteMessage();
                m.type = RemoteMessage.MessageType.CHAT;
                m.chatText = t;
                out.writeObject(m);
                out.flush();
            } catch (IOException x) {}
        }
        public void sendFileMessage(File f) {
            try {
                byte[] d = readFile(f);
                if (d == null) return;
                RemoteMessage m = new RemoteMessage();
                m.type = RemoteMessage.MessageType.FILE;
                m.fileName = f.getName();
                m.fileData = d;
                out.writeObject(m);
                out.flush();
            } catch (IOException x) {}
        }
        private byte[] readFile(File f) {
            try {
                byte[] b = new byte[(int) f.length()];
                try (FileInputStream fis = new FileInputStream(f)) {
                    int r = fis.read(b);
                    if (r != b.length) return null;
                }
                return b;
            } catch (IOException x) {
                return null;
            }
        }
        public void openChatWindow() {
            if (chatWindow == null) chatWindow = new ClientChatWindow(this);
            if (!chatWindow.isVisible()) chatWindow.setVisible(true);
        }
        private void handleRemoteEvent(RemoteEvent evt) {
            if (evt.type == RemoteEvent.Type.MOUSE) {
                Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
                int realX = (int) (((double) evt.x / evt.displayWidth) * size.width);
                int realY = (int) (((double) evt.y / evt.displayHeight) * size.height);
                robot.mouseMove(realX, realY);
                if (evt.mouseID == MouseEvent.MOUSE_PRESSED) {
                    if (evt.button == MouseEvent.BUTTON1) robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    else if (evt.button == MouseEvent.BUTTON3) robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                } else if (evt.mouseID == MouseEvent.MOUSE_RELEASED) {
                    if (evt.button == MouseEvent.BUTTON1) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    else if (evt.button == MouseEvent.BUTTON3) robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                }
            } else if (evt.type == RemoteEvent.Type.KEYBOARD) {
                if (evt.keyID == KeyEvent.KEY_PRESSED) robot.keyPress(evt.keyCode);
                else if (evt.keyID == KeyEvent.KEY_RELEASED) robot.keyRelease(evt.keyCode);
            }
        }
    }
    static class ClientGUI extends JFrame {
        private JButton sendFileButton, stopConnButton, openChatButton;
        private RemoteDesktopClient controller;
        private JLabel infoLabel;
        public ClientGUI() {
            super("Client – Remote Controlled");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(600, 400);
            setLayout(new BorderLayout());
            infoLabel = new JLabel("Your system is being remotely controlled.", SwingConstants.CENTER);
            add(infoLabel, BorderLayout.NORTH);
            JPanel p = new JPanel();
            openChatButton = new JButton("Open Chat");
            openChatButton.addActionListener(e -> {
                if (controller != null) controller.openChatWindow();
            });
            sendFileButton = new JButton("Send File");
            sendFileButton.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File f = fc.getSelectedFile();
                    if (f.length() <= 10 * 1024 * 1024) {
                        if (controller != null) controller.sendFileMessage(f);
                    } else JOptionPane.showMessageDialog(this, "File exceeds 10 MB limit.");
                }
            });
            stopConnButton = new JButton("Stop Connection");
            stopConnButton.addActionListener(e -> {
                if (controller != null) controller.stopConnection();
            });
            p.add(openChatButton);
            p.add(sendFileButton);
            p.add(stopConnButton);
            add(p, BorderLayout.SOUTH);
        }
        public void setController(RemoteDesktopClient c) {
            controller = c;
        }
        public void setControlledBy(String h) {
            infoLabel.setText("Your system is being remotely controlled by " + h);
        }
        public void showConnectionStopped() {
            infoLabel.setText("Connection stopped.");
        }
    }
    static class ClientChatWindow extends JFrame {
        private JTextArea area;
        private JTextField input;
        private RemoteDesktopClient controller;
        public ClientChatWindow(RemoteDesktopClient c) {
            super("Chat");
            controller = c;
            setSize(600, 400);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            area = new JTextArea();
            area.setEditable(false);
            add(new JScrollPane(area), BorderLayout.CENTER);
            input = new JTextField();
            JButton send = new JButton("Send");
            JPanel p = new JPanel(new BorderLayout());
            p.add(input, BorderLayout.CENTER);
            p.add(send, BorderLayout.EAST);
            add(p, BorderLayout.SOUTH);
            send.addActionListener(e -> {
                String t = input.getText().trim();
                if (!t.isEmpty()) {
                    appendChatMessage("You: " + t);
                    controller.sendChatMessage(t);
                    input.setText("");
                }
            });
        }
        public void appendChatMessage(String m) {
            area.append(m + "\n");
        }
    }
    static class RemoteEvent implements Serializable {
        enum Type { MOUSE, KEYBOARD }
        public Type type;
        public int mouseID, button, x, y, displayWidth, displayHeight, keyID, keyCode;
        public char keyChar;
    }
    static class RemoteMessage implements Serializable {
        enum MessageType { CHAT, FILE }
        public MessageType type;
        public String chatText, fileName;
        public byte[] fileData;
    }
    static class ConnectionEntry {
        private String ip;
        private int port;
        private boolean online;
        public ConnectionEntry(String i, int p) {
            ip = i;
            port = p;
        }
        public String getIp() {
            return ip;
        }
        public int getPort() {
            return port;
        }
        public void setOnline(boolean o) {
            online = o;
        }
        public boolean isOnline() {
            return online;
        }
        public String toString() {
            return ip + ":" + port + " - " + (online ? "Online" : "Offline");
        }
    }
    static class ClientLauncher extends JFrame {
        private DefaultComboBoxModel<ConnectionEntry> model;
        private JComboBox<ConnectionEntry> combo;
        private JTextField portField;
        private JLabel status;
        private static final String FILE_NAME = "connections.txt";
        public ClientLauncher() {
            super("Client Launcher");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(400, 170);
            setLayout(new FlowLayout());
            model = new DefaultComboBoxModel<>();
            combo = new JComboBox<>(model);
            combo.setEditable(true);
            combo.setEditor(new BasicComboBoxEditor() {
                public void setItem(Object o) {
                    if (o instanceof ConnectionEntry) editor.setText(((ConnectionEntry) o).getIp());
                    else if (o != null) editor.setText(o.toString());
                    else editor.setText("");
                }
                public Object getItem() {
                    return editor.getText();
                }
            });
            combo.setRenderer(new DefaultListCellRenderer() {
                public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                    if (v instanceof ConnectionEntry) v = ((ConnectionEntry) v).getIp();
                    return super.getListCellRendererComponent(l, v, i, s, f);
                }
            });
            portField = new JTextField("5000", 6);
            JButton connectBtn = new JButton("Connect");
            add(new JLabel("IP:"));
            add(combo);
            add(new JLabel("Port:"));
            add(portField);
            add(connectBtn);
            status = new JLabel(" ");
            add(status);
            List<ConnectionEntry> saved = loadConnections();
            if (saved.isEmpty()) {
                try {
                    String d = InetAddress.getLocalHost().getHostAddress();
                    model.addElement(new ConnectionEntry(d, Integer.parseInt(portField.getText().trim())));
                } catch (Exception ex) {
                    model.addElement(new ConnectionEntry("127.0.0.1", Integer.parseInt(portField.getText().trim())));
                }
            } else {
                for (ConnectionEntry ce : saved) model.addElement(ce);
            }
            updateCount();
            connectBtn.addActionListener(e -> {
                String ip = combo.getEditor().getItem().toString().trim();
                int p;
                try {
                    p = Integer.parseInt(portField.getText().trim());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Invalid port");
                    return;
                }
                ConnectionEntry sel = null;
                for (int i = 0; i < model.getSize(); i++) {
                    ConnectionEntry c = model.getElementAt(i);
                    if (c.getIp().equals(ip) && c.getPort() == p) {
                        sel = c;
                        break;
                    }
                }
                if (sel == null) {
                    sel = new ConnectionEntry(ip, p);
                    model.addElement(sel);
                }
                saveConnections(getAll());
                setVisible(false);
                RemoteDesktopClient cl = new RemoteDesktopClient();
                cl.startClient(ip, p, sel, this);
            });
        }
        private List<ConnectionEntry> getAll() {
            List<ConnectionEntry> l = new ArrayList<>();
            for (int i = 0; i < model.getSize(); i++) l.add(model.getElementAt(i));
            return l;
        }
        private void updateCount() {
            int on = 0, off = 0;
            for (int i = 0; i < model.getSize(); i++) {
                ConnectionEntry c = model.getElementAt(i);
                if (c.isOnline()) on++;
                else off++;
            }
            status.setText("Online: " + on + "   Offline: " + off);
        }
        private List<ConnectionEntry> loadConnections() {
            List<ConnectionEntry> list = new ArrayList<>();
            File f = new File(FILE_NAME);
            if (!f.exists()) return list;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.trim().split(":");
                    if (parts.length == 2) list.add(new ConnectionEntry(parts[0], Integer.parseInt(parts[1])));
                }
            } catch (Exception x) {}
            return list;
        }
        private void saveConnections(List<ConnectionEntry> l) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
                for (ConnectionEntry c : l) pw.println(c.getIp() + ":" + c.getPort());
            } catch (Exception x) {}
        }
    }
}