package com.insightsystems.dal.serverlink;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.HttpCommunicator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.singletonList;

/*******
 ***** v1.0
 * - Initial Version
 *
 ***** v 1.1
 *  - Added additional stats - FirmwareVersion and Mac-address
 *
 ***** v1.2
 *  - Updated controls to AdvancedControllableProperties
 *
 */
public class SLPSB1008H extends HttpCommunicator implements Monitorable, Pingable, Controller {
    private final String[][] pduInfo = new String[8][2];
    /****
        pduInfo[outlets,info]
                 |        |
             PDU outlet   |
               0 - 7      |
                        0 or 1
               outletName    outletState
     */
    public SLPSB1008H(){
        this.setLogin("snmp");
        this.setPassword("1234");
        this.setProtocol("http");
        this.setPort(80);
        this.setAuthenticationScheme(AuthenticationScheme.Basic);
        this.setContentType("application/xml");
    }

    @Override
    protected void authenticate() { }

    public List<Statistics> getMultipleStatistics() throws Exception {
        getNames();
        final String currentDraw = getStatus();
        ExtendedStatistics deviceStatistics = new ExtendedStatistics();
        Map<String,String> statistics = new HashMap<>();
        List<AdvancedControllableProperty> controls = new ArrayList<>();

        for (String[] port : pduInfo) {
            statistics.put(port[0], port[1]);
            controls.add(new AdvancedControllableProperty(port[0],new Date(),createSwitch(),port[1]));
        }
        statistics.put("CurrentDraw",currentDraw);
        final String systemPage = this.doGet("/system.htm");
        statistics.put("FirmwareVersion",regexFind(systemPage,">Firmware\\s+Version</font>[\\s\\S]+?>([.:[-]\\w\\d]+)</font>"));
        statistics.put("MacAddress",regexFind(systemPage,">MAC\\s+Address</font>[\\s\\S]+?>([.:\\w\\d]+)</font>"));
        deviceStatistics.setStatistics(statistics);
        deviceStatistics.setControllableProperties(controls);

        return singletonList(deviceStatistics);
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        if (cp == null)
            return;

        for (int i = 0;i<pduInfo.length; i++){
            if (pduInfo[i][0].equals(cp.getProperty())){
                switch (String.valueOf(cp.getValue())){
                    case "0":
                        controlOutlets(""+i,false);
                        break;
                    case "1":
                        controlOutlets(""+i,true);
                        break;
                    default: //in case control value is invalid for some reason.
                }
                break; //Already found name, no need to loop through the rest.
            }
        }

    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        StringBuilder controlOn = new StringBuilder();
        StringBuilder controlOff = new StringBuilder();
        for (ControllableProperty cp : list){
            if (cp == null)
                continue;

            for (int i = 0;i<pduInfo.length; i++){
                if (pduInfo[i][0].equals(cp.getProperty())){
                    switch ((int)cp.getValue()){
                        case 0:
                            controlOff.append(i);
                            break;
                        case 1:
                            controlOn.append(i);
                            break;
                        default:
                    }
                    break; //Already found name, no need to loop through the rest.
                }
            }
        }
        controlOutlets(controlOn.toString(),true);
        controlOutlets(controlOff.toString(),false);
    }

    private void getNames() throws Exception {
        String rawNames = this.doPost("/Getname.xml",""); //Get port status form the pdu
        rawNames = rawNames.replaceAll("<response>","").replaceAll("</response>","");
        for (int n=0;n < 8;n++){
            pduInfo[n][0] = regexFind(rawNames,"<na"+n+">([\\s\\w]*)(?:,[\\s\\w]*)*</na"+n+">?");
        }

    }

    private String getStatus() throws Exception {
        String devResponse = this.doPost("/status.xml", ""); //Get status from the pdu
        devResponse = regexFind(devResponse, "<pot0>([0-9,.]*)</pot0>");
        final String[] split = devResponse.split(",");
        for (int n = 0; n < 8; n++) {
            pduInfo[n][1] = split[n+10];
        }
        return split[2];
    }

    private AdvancedControllableProperty.Switch createSwitch(){
        AdvancedControllableProperty.Switch aSwitch = new AdvancedControllableProperty.Switch();
        aSwitch.setLabelOn("On");
        aSwitch.setLabelOff("off");
        return aSwitch;
    }

    private void controlOutlets(String outletString,boolean requestedState) throws Exception{
        final char[] outlets = outletString.toCharArray();
        char[] control = {'0','0','0','0','0','0','0','0'};
        for (char outlet : outlets) {
            control[outlet - '0'] = '1';
        }
        if (requestedState) {
            doPost("/ons.cgi?led=" + stringifyChars(control), "");
        } else {
            doPost("/offs.cgi?led=" + stringifyChars(control), "");
        }
    }

    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String stringifyChars(char[] chars){
        StringBuilder output = new StringBuilder();
        for (char c : chars){
            output.append(c);
        }
        return output.toString();
    }

    public static void main(String[] args) throws Exception {
        SLPSB1008H test = new SLPSB1008H();
        test.setHost("10.164.69.10");
        test.init();

        ExtendedStatistics res = (ExtendedStatistics) test.getMultipleStatistics().get(0);
        System.out.println("Statistics.");
        res.getStatistics().forEach((k, v) -> System.out.println(k + " : " + v));
    }
}

