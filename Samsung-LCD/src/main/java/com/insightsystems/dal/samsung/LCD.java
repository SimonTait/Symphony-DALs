package com.insightsystems.dal.samsung;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.TelnetCommunicator;

import java.util.List;

public class LCD extends TelnetCommunicator implements Pingable, Controller, Monitorable {
    private final String messageHeader = "\u00AA";
    private final String queryPower = "\u0011\u0001\u0000\u0012",queryInput = "\u0014\u0001\u0000\u0015"; //Device must be set to ID 1 for queries to work

    private final String[] powerCommands  = {"\u0011\u00FE\u0001\u0000","\u0011\u00FE\u0001\u0001"};
    private final String[] powerResponses = {"\u00AA\u00FF\u0001\u0003\u0041\u0011\u0000","\u00AA\u00FF\u0001\u0003\u0041\u0011\u0001"};

    private final String[] sourceNames = {"PC","BNC","DVI","AV","S-Video","Component","MagicNet","DVI 2","HDMI 1","HDMI 2","DisplayPort","HDMI 3"};
    private final String[] sourceCommands = {"\u0014\u00FE\u0001\u0014","\u0014\u00FE\u0001\u001E","\u0014\u00FE\u0001\u0018","\u0014\u00FE\u0001\u000C","\u0014\u00FE\u0001\u0004","\u0014\u00FE\u0001\u0008",
            "\u0014\u00FE\u0001\u0060","\u0014\u00FE\u0001\u001F","\u0014\u00FE\u0001\u0021","\u0014\u00FE\u0001\u0023","\u0014\u00FE\u0001\u0025","\u0014\u00FE\u0001\u0026"};
    private final String[] sourceResponses = {};


    private final char RSP_DISPLAY_INPUT_1[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x14};	//PC
    private final char RSP_DISPLAY_INPUT_2[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x1E};	//BNC
    private final char RSP_DISPLAY_INPUT_3[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x18};	//DVI
    private final char RSP_DISPLAY_INPUT_4[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x0C};	//AV
    private final char RSP_DISPLAY_INPUT_5[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x04};	//S-Video
    private final char RSP_DISPLAY_INPUT_6[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x08};	//Component
    private final char RSP_DISPLAY_INPUT_7[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x60};	//MagicNet
    private final char RSP_DISPLAY_INPUT_8[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x1F};	//DVI
    private final char RSP_DISPLAY_INPUT_9[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x40};	//TV
    private final char RSP_DISPLAY_INPUT_10[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x21};	//HDMI 1
    private final char RSP_DISPLAY_INPUT_11[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x23};	//HDMI 2
    private final char RSP_DISPLAY_INPUT_12[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x25};	//Display Port
    private final char RSP_DISPLAY_INPUT_13[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x26};	//HDMI 3
    private final char RSP_DISPLAY_INPUT_14[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x31};	//HDMI 3_ - DM82D
    private final char RSP_DISPLAY_INPUT_15[]			= {0xAA,0xFF,0x01,0x03,0x41,0x14,0x22};	//HDMI 1_ - UH46F5

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {

    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {

    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        return null;
    }


    private char checkSum(char[] _sString)
    {
        char _cReturnVal = 0;
        for(char _cPos = (char)_sString.length; _cPos > 0; _cPos--)
            _cReturnVal = (char)(_cReturnVal + _sString[_cPos]);
        return _cReturnVal;
    }


    private static String toReadable(char[] chars){
        StringBuilder string = new StringBuilder();
        for (char c :chars){
            string.append("0x");
            string.append(Integer.toHexString(c).length() != 2 ? "0" + Integer.toHexString(c) : Integer.toHexString(c));
            string.append(" ");
        }
        return string.toString();
    }


    public static void main(String[] args) {
    }
}
