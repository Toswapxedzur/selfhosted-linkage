package net.blopybox.selfhosted_linkage.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Path;

/** Standalone check of the transport core (no Minecraft): relay + selector → localhost port → read the server's first line. */
public final class CoreSmoke {
    static void vw(java.io.DataOutputStream o,int v)throws java.io.IOException{while((v&~0x7F)!=0){o.writeByte((v&0x7F)|0x80);v>>>=7;}o.writeByte(v);}
    static int rv(java.io.DataInputStream i)throws java.io.IOException{int n=0,sh=0,b;do{b=i.readUnsignedByte();n|=(b&0x7F)<<sh;sh+=7;}while((b&0x80)!=0);return n;}
    public static void main(String[] a) throws Exception {
        String relay = a[0], selector = a[1];
        Path state = Path.of(a.length > 2 ? a[2] : System.getProperty("java.io.tmpdir"), "linkage-smoke");
        long t0 = System.nanoTime();
        try (LinkageSession s = LinkageSession.start(relay, selector, state, l -> System.out.println("   " + l))) {
            System.out.printf("[%s #%s] transport=%s ready in %d ms on 127.0.0.1:%d%n", relay, selector,
                    s.target().transport(), (System.nanoTime() - t0) / 1_000_000, s.localPort());
            for (int i = 1; i <= 3; i++) {
                long t = System.nanoTime();
                try (Socket c = new Socket("127.0.0.1", s.localPort())) {
                    c.setTcpNoDelay(true); c.setSoTimeout(20000);
                    java.io.DataOutputStream o = new java.io.DataOutputStream(c.getOutputStream());
                    java.io.DataInputStream in = new java.io.DataInputStream(new BufferedReader(new InputStreamReader(c.getInputStream())) == null ? c.getInputStream() : c.getInputStream());
                    // handshake (next=1) + status request, MC 1.21.1 protocol 767
                    java.io.ByteArrayOutputStream hb = new java.io.ByteArrayOutputStream(); java.io.DataOutputStream ho = new java.io.DataOutputStream(hb);
                    vw(ho,0); vw(ho,767); byte[] hn="127.0.0.1".getBytes(); vw(ho,hn.length); ho.write(hn); ho.writeShort(s.localPort()); vw(ho,1);
                    vw(o,hb.size()); o.write(hb.toByteArray()); o.write(new byte[]{1,0}); o.flush();
                    rv(in); rv(in); int len=rv(in); byte[] j=new byte[len]; in.readFully(j); String js=new String(j);
                    String ver=js.replaceAll("(?s).*\"name\"\\s*:\\s*\"([^\"]+)\".*","$1");
                    System.out.printf("   MC status %d → %d chars, version=%s  (%d ms)%n", i, js.length(), ver.length()>40?"?":ver, (System.nanoTime() - t) / 1_000_000);
                }
            }
        }
        System.out.println("   session closed cleanly");
    }
}
