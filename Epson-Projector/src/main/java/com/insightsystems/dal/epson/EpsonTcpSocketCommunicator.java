package com.insightsystems.dal.epson;

import com.avispl.symphony.dal.communicator.ShellCommunicator;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class EpsonTcpSocketCommunicator extends ShellCommunicator {
    private final byte[] handshake = { 0x45, 0x53, 0x43, 0x2F, 0x56, 0x50, 0x2E, 0x6E, 0x65, 0x74, 0x10, 0x03, 0x00, 0x00, 0x00, 0x00 };
    private Socket clientSocket;
    private OutputStream out;
    private InputStream in;
    private int timeout = 1000;

    @Override
    protected void createChannel() throws Exception {
        //System.out.println("Creating Channel");
        clientSocket = new Socket(this.getHost(), this.getPort());
        out = clientSocket.getOutputStream();
        in = clientSocket.getInputStream();
        out.write(handshake);

    }

    @Override
    protected void destroyChannel() {
        try {
            in.close();
            out.close();
            clientSocket.close();
        } catch (Exception ignored){}

    }

    @Override
    protected String internalSend(String command) throws Exception {
        if (in == null || out == null) {
            createChannel();
        }
        try {
            // flush input stream
            if (in.markSupported()) {
                in.reset();
            } else {
                while (in.available() > 0) {
                    int availableBytes = in.available();
                    if (availableBytes > 0) {
                        for (int i = 0; i < availableBytes; i++){
                            in.read();
                        }
                    }
                }
            }
        } catch(Exception e){
            System.out.println("Exceptinal!");
        }


        //System.out.println("Sending command: " + command.replaceAll("\r","r").replaceAll("\n","n"));
        out.write(command.getBytes());
        out.write("\r\n".getBytes());
        out.flush();
        String resp = "";
        long elapsedTime = 0L;
        long startTime = System.currentTimeMillis();
        while (elapsedTime < this.timeout) {
            int availableBytes = in.available();
            if (availableBytes > 0) {
                //System.out.println("Available Bytes: " + availableBytes);
                for (int i = 0; i < availableBytes; i++) {
                    if (i <= availableBytes-3){ //Last few bytes are garbage and mess make the whole string disappear somehow..
                        resp += (char) in.read();
                    } else {
                        in.read();
                    }
                }
                if (resp.startsWith("ESC/VP.net")){
                    if (resp.length() >= 16) {
                        resp = resp.substring(16);
                    }
                }
                return resp;
            } else {
                Thread.sleep(100);
            }
            elapsedTime = System.currentTimeMillis() - startTime;
        }

        return resp;
    }

    @Override
    protected boolean isChannelConnected() {
        return (in == null || out == null || clientSocket == null);
    }
}