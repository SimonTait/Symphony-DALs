package com.insightsystems.dal.nec;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.snmp.SnmpEntry;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;

import java.util.*;

public class LCD extends TcpSocketCommunicator implements Pingable, Controller, Monitorable {
    final String HEX_DIGITS = "0123456789ABCDEF";
    final byte[] cmdHeader = hexStringToByteArray("01"), terminator = hexStringToByteArray("0D");

    final byte[] cmdPowerOn = hexStringToByteArray("304130413043024332303344363030303103");
    final byte[] cmdPowerOff  = hexStringToByteArray("304130413043024332303344363030303403");
    final byte[][] cmdSource = {
            hexStringToByteArray("30413045304102303036303030303503"),//Composite
            hexStringToByteArray("30413045304102303036303030303703"),//S-Video 1
            hexStringToByteArray("30413045304102303036303030304303"),//Component 1
            hexStringToByteArray("30413045304102303036303030304503"),//Component 2
            hexStringToByteArray("30413045304102303036303030303303"),//DVI a
            hexStringToByteArray("30413045304102303036303030303103"),//RGB 1
            hexStringToByteArray("30413045304102303036303030303203"),//RGB 2
            hexStringToByteArray("30413045304102303036303030303403"),//HDMI
            hexStringToByteArray("30413045304102303036303030304603"),//DisplayPort 1
            hexStringToByteArray("30413045304102303036303030304403"),//Internal PC
            hexStringToByteArray("30413045304102303036303030313103"),//HDMI 1
            hexStringToByteArray("30413045304102303036303030313203"),//HDMI 2
    };
    final String[] cmdSourceNames = {"Composite","S-Video 1","Component 1","Component 2","DVI a","RGB 1","RGB 2","HDMI","DisplayPort 1","Internal PC","HDMI 1","HDMI 2"};

    final byte[] queryPower  = hexStringToByteArray("0130413041303602303144360374");
    final byte[] querySource = hexStringToByteArray("0130413043303602303036300303");
    final byte[][] rspPower = {
            hexStringToByteArray("01303041423132023032303044363030303030343030303403710D"), //Off
            hexStringToByteArray("01303041423132023032303044363030303030343030303103740D"), //On
            hexStringToByteArray("01303041423132023032303044363030303030343030303203770D"), //On Standby
            hexStringToByteArray("01303041423132023032303044363030303030343030303303760D")  //On Suspend
    };
    final String[] rspPowerNames = {"Off","On","On- Standby","On- Suspend"};

    final String[] snmpNames = {"deviceModel","serialNumber","power","physicalHeight","physicalWidth","inputSource","volume"};
    final Collection<String> snmpOids =  new ArrayList<String>(){{
        add("1.3.6.1.4.1.2699.1.4.1.2.1.0"); //Model
        add("1.3.6.1.4.1.2699.1.4.1.2.2.0"); //SerialNumber
        add("1.3.6.1.4.1.2699.1.4.1.4.2.0"); //PowerState 7- standby,11- power on, 6- powersaving
        add("1.3.6.1.4.1.2699.1.4.1.5.3.0"); // PhysicalHeight
        add("1.3.6.1.4.1.2699.1.4.1.5.4.0"); //physical width
        add("1.3.6.1.4.1.2699.1.4.1.12.2.0"); //currend video input
        add("1.3.6.1.4.1.2699.1.4.1.16.2.0"); //get/set audio volume
    }};


    public LCD(){
        this.setPort(7142);
        this.setLogin("");
        this.setPassword("");
        this.setLoginSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList(""));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setCommandErrorList(Collections.singletonList(""));
        this.setSnmpCommunity("public");
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStat = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();

//        System.out.println(toReadable(sendCommandToDevice(cmdPowerOn)));
//
//        Thread.sleep(10000L);

        Collection<SnmpEntry> snmpEntries = querySnmp(snmpOids);
        int i=0;
        for (SnmpEntry entry : snmpEntries) {
            if (i == 0)
                entry.setValue(entry.getValue().replace("NEC/",""));
            stats.put(snmpNames[i],entry.getValue());
            i++;
        }
        stats.put("power",getPowerState(sendQueryToDevice(queryPower)));


        extStat.setStatistics(stats);
        return Collections.singletonList(extStat);
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
//        byte[] response = sendCommandToDevice(cmdPowerOn);
//        System.out.println(toReadable(response));
        //reboot
        //Request URL: http://10.219.65.150/Forms/reboot_1
        //body 'Submit=Reboot'
        //url for encoded headers
        switch (cp.getProperty()){
            case "reboot":
                break;

            case "volume":

                break;

            case "power":
                break;

            case "inputSource":
                break;

            default:
                this.logger.warn("Controllable property not found for " + cp.getProperty());
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty cp : list){
            this.controlProperty(cp);
        }
    }

    private String getPowerState(byte[] response) {
        for (int i= 0; i < rspPower.length;i++){
            if (Arrays.equals(rspPower[i],response)){
                return rspPowerNames[i];
            }
        }
        return "Error- Power state unknown.";
    }

    private byte[] sendCommandToDevice(byte[] command) throws Exception {
        byte[] commandArray = new byte[command.length+3];//
        byte checksum = 0;
        commandArray[0] = cmdHeader[0];
        for (int i = 0; i < command.length;i++) {
            commandArray[i + 1] = command[i];
            checksum = (byte) (checksum ^ command[i]);
        }

        commandArray[commandArray.length-2] = checksum;
        commandArray[commandArray.length-1] = terminator[0];
        return customSend(commandArray);
    }

    private byte[] sendQueryToDevice(byte[] command) throws Exception {
        byte[] commandArray = new byte[command.length+1];
        for (int i = 0; i < command.length;i++)
            commandArray[i] = command[i];

        commandArray[command.length] = terminator[0];
        return customSend(commandArray);
    }

    private byte[] XorChecksum(byte[] command){
        byte[] returnArray = new byte[command.length+1];
        byte checksum = 0;
        for (int i = command.length-1;i >= 0;i--){
            checksum = (byte) (checksum ^ command[i]);
            returnArray[i] = command[i];
        }
        returnArray[command.length] = checksum;
        return returnArray;
    }

    private String toReadable(byte[] data) {
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xff;

            buf.append(HEX_DIGITS.charAt(v >> 4));
            buf.append(HEX_DIGITS.charAt(v & 0xf));

            buf.append(" ");
        }

        return buf.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static void main(String[] args) throws Exception {
        LCD test = new LCD();
        test.setHost("10.219.65.150");
        test.init();
        ((ExtendedStatistics)test.getMultipleStatistics().get(0)).getStatistics().forEach((k,v)-> System.out.println(k + " : " + v));
    }
}
