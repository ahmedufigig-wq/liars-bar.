package com.liarsbar.game;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(name = "Lan")
public class LanPlugin extends Plugin {
  private ServerSocket server;
  private final Map<String, Socket> socks = new ConcurrentHashMap<>();
  private final Map<String, PrintWriter> outs = new ConcurrentHashMap<>();
  private Socket cs;
  private PrintWriter cout;
  private int seq = 0;

  private void ev(String name, String id, String data) {
    JSObject o = new JSObject();
    if (id != null) o.put("id", id);
    if (data != null) o.put("data", data);
    notifyListeners(name, o);
  }

  private PrintWriter writer(Socket s) throws Exception {
    return new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
  }

  @PluginMethod
  public void getIp(PluginCall call) {
    JSArray a = new JSArray();
    try {
      for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        if (!n.isUp() || n.isLoopback()) continue;
        for (InetAddress i : Collections.list(n.getInetAddresses())) {
          if (i instanceof Inet4Address && !i.isLoopbackAddress()) a.put(i.getHostAddress());
        }
      }
    } catch (Exception e) {}
    JSObject r = new JSObject();
    r.put("ips", a);
    call.resolve(r);
  }

  @PluginMethod
  public void startServer(final PluginCall call) {
    stopAll();
    try {
      final ServerSocket ss = new ServerSocket();
      ss.setReuseAddress(true);
      ss.bind(new InetSocketAddress(call.getInt("port", 8765)));
      server = ss;
      new Thread(new Runnable() {
        public void run() {
          try {
            while (!ss.isClosed()) handle(ss.accept());
          } catch (Exception e) {}
        }
      }).start();
      call.resolve();
    } catch (Exception e) {
      call.reject("server failed");
    }
  }

  private void handle(final Socket s) {
    final String id = "c" + (++seq);
    try {
      s.setTcpNoDelay(true);
      outs.put(id, writer(s));
    } catch (Exception e) {
      return;
    }
    socks.put(id, s);
    ev("join", id, null);
    new Thread(new Runnable() {
      public void run() {
        try {
          BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
          String l;
          while ((l = br.readLine()) != null) ev("msg", id, l);
        } catch (Exception e) {
        } finally {
          socks.remove(id);
          outs.remove(id);
          try { s.close(); } catch (Exception e) {}
          ev("leave", id, null);
        }
      }
    }).start();
  }

  @PluginMethod
  public void send(PluginCall call) {
    PrintWriter w = outs.get(call.getString("id"));
    if (w != null) w.println(call.getString("data"));
    call.resolve();
  }

  @PluginMethod
  public void broadcast(PluginCall call) {
    String d = call.getString("data");
    for (PrintWriter w : outs.values()) w.println(d);
    call.resolve();
  }

  @PluginMethod
  public void kick(PluginCall call) {
    Socket s = socks.get(call.getString("id"));
    if (s != null) try { s.close(); } catch (Exception e) {}
    call.resolve();
  }

  @PluginMethod
  public void connect(final PluginCall call) {
    stopAll();
    final String host = call.getString("host");
    final int port = call.getInt("port", 8765);
    new Thread(new Runnable() {
      public void run() {
        Socket s = new Socket();
        try {
          s.connect(new InetSocketAddress(host, port), 5000);
          s.setTcpNoDelay(true);
          cs = s;
          cout = writer(s);
          call.resolve();
        } catch (Exception e) {
          try { s.close(); } catch (Exception x) {}
          call.reject("connect failed");
          return;
        }
        try {
          BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
          String l;
          while ((l = br.readLine()) != null) ev("cmsg", null, l);
        } catch (Exception e) {
        } finally {
          ev("cclose", null, null);
        }
      }
    }).start();
  }

  @PluginMethod
  public void sendHost(PluginCall call) {
    if (cout != null) cout.println(call.getString("data"));
    call.resolve();
  }

  @PluginMethod
  public void stop(PluginCall call) {
    stopAll();
    call.resolve();
  }

  private void stopAll() {
    try { if (server != null) server.close(); } catch (Exception e) {}
    server = null;
    for (Socket s : socks.values()) try { s.close(); } catch (Exception e) {}
    try { if (cs != null) cs.close(); } catch (Exception e) {}
    cs = null;
    cout = null;
  }
}
