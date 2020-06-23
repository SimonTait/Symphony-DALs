package com.insightsystems.dal.poly;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.*;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.TelnetCommunicator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.round;

/*****
 * https://www.polycom.com/content/dam/polycom-support/products/telepresence-and-video/g7500/user/en/g7500-command-line-api-reference-guide.pdf
 *
 **** v1.0
 *
 */
public class x30 extends TelnetCommunicator implements Controller, Monitorable, Pingable {
    final String queryGatekeeperIp = "gatekeeperip get", querySipRegistrar = "systemsetting get sipregistrarserver",queryH323Enable ="systemsetting get iph323enable",querySipEnable = "systemsetting get sipenable",queryStatus = "status";
    final String queryNetStats = "netstats",queryCallInfo = "callinfo all",queryUptime = "uptime get",queryDeviceInfo = "whoami",queryAdvStats = "advnetstats",queryNearAudioMute = "mute near get",queryNearVideoMute = "videomute near get";
    final String queryCalendarEnabled = "calendarregisterwithserver get",queryCalendarResource = "calendarresource get",queryCalendarServer= "calendarserver get",queryCalendarStatus = "calendarstatus get", queryVolume = "volume get";
    final String cmdReboot = "reboot now",cmdWake = "wake",cmdVolume = "volume set ";

    public x30(){
        this.setPort(24);
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setCommandErrorList(Collections.singletonList(""));
        this.setLoginSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList(""));
        this.setPasswordPrompt("Password:\n");
    }



    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        EndpointStatistics endStats = new EndpointStatistics();
        ExtendedStatistics extStat = new ExtendedStatistics();

        final String netStats = send(queryNetStats);
        final String status = send(queryStatus);

        endStats.setCallStats(getCallStats(netStats));
        endStats.setRegistrationStatus(getRegistrationStatus(status));
        endStats.setInCall(getInCall());

        if (endStats.isInCall()) {
            final String advNetStats = send(queryAdvStats);
            endStats.setAudioChannelStats(getAudioChannelStats(netStats,advNetStats));
            endStats.setVideoChannelStats(getVideoChannelStats(netStats,advNetStats));
            endStats.setContentChannelStats(getContentChannelStats(netStats,advNetStats));
        }

        final float volume = parseFloat(send(queryVolume));
        extStat.setControllableProperties(getControls(volume));
        extStat.setStatistics(getSystemStatistics(status,volume));
        return new ArrayList<Statistics>(){{add(endStats);add(extStat);}};
    }

    private List<AdvancedControllableProperty> getControls(float volume) {
        List<AdvancedControllableProperty> controlProperties = new ArrayList<>();

        AdvancedControllableProperty.Button rebootButton = new AdvancedControllableProperty.Button();
        rebootButton.setGracePeriod(20000L);
        rebootButton.setLabel("Reboot");
        rebootButton.setLabelPressed("Rebooting..");
        controlProperties.add(new AdvancedControllableProperty("reboot",new Date(),rebootButton,"0"));

        AdvancedControllableProperty.Button wakeButton = new AdvancedControllableProperty.Button();
        wakeButton.setLabel("Wake");
        wakeButton.setLabelPressed("Waking");
        wakeButton.setGracePeriod(1000L);
        controlProperties.add(new AdvancedControllableProperty("wake",new Date(),wakeButton,"0"));

        AdvancedControllableProperty.Slider volumeSlider = new AdvancedControllableProperty.Slider();
        volumeSlider.setLabelStart("0%");
        volumeSlider.setLabelEnd("100%");
        volumeSlider.setRangeStart(0F);
        volumeSlider.setRangeEnd(50F);
        controlProperties.add(new AdvancedControllableProperty("volume",new Date(),volumeSlider,volume));


        return controlProperties;
    }

    private ContentChannelStats getContentChannelStats(String netStats, String advNetStats) {
        ContentChannelStats contentStats = new ContentChannelStats();
        contentStats.setBitRateRx(parseInt(regexFind(advNetStats,"rcru:([\\d]+(?: K)?)")));
        contentStats.setBitRateTx(parseInt(regexFind(advNetStats,"tcru:([\\d]+(?: K)?)")));
        contentStats.setFrameRateRx(parseFloat(regexFind(advNetStats,"rcfr:([\\d.]+)")));
        contentStats.setFrameRateTx(parseFloat(regexFind(advNetStats,"tcfr:([\\d.]+)")));
        contentStats.setPacketLossRx(parseInt(regexFind(advNetStats,"rcpl:([\\d]+)")));
        contentStats.setPacketLossTx(parseInt(regexFind(advNetStats,"tcpl:([\\d]+)")));
        contentStats.setCodec(regexFind(netStats,"tcp:[\\s\\S\\r\\n]+tcp:([^ ]+)")); //There are 2 fields labelled tcp, we want the second one here
        return contentStats;
    }

    private VideoChannelStats getVideoChannelStats(String netStats, String advNetStats) throws Exception {
        VideoChannelStats videoStats = new VideoChannelStats();
        videoStats.setFrameRateRx(parseFloat(regexFind(advNetStats,"rvfr:([\\d.]+)")));
        videoStats.setFrameRateTx(parseFloat(regexFind(advNetStats,"tvfr:([\\d.]+)")));
        videoStats.setBitRateRx(parseInt(regexFind(advNetStats,"rvru:([\\d]+(?: K)?)")));
        videoStats.setBitRateTx(parseInt(regexFind(advNetStats,"tvru:([\\d]+(?: K)?)")));
        videoStats.setJitterRx(parseFloat(regexFind(advNetStats,"rvj:([\\d.]+)")));
        videoStats.setJitterTx(parseFloat(regexFind(advNetStats,"tvj:([\\d.]+)")));
        videoStats.setPacketLossRx(parseInt(regexFind(advNetStats,"rvpl:([\\d]+)")));
        videoStats.setPacketLossTx(parseInt(regexFind(advNetStats,"tvpl:([\\d]+)")));
        videoStats.setCodec(regexFind(netStats,"tvp:([^ ]+)"));
        videoStats.setMuteTx(send(queryNearVideoMute).contains("on"));
        return videoStats;
    }

    private AudioChannelStats getAudioChannelStats(String netStats,String advStats) throws Exception {
        AudioChannelStats audioStats = new AudioChannelStats();
        audioStats.setBitRateRx(parseInt(regexFind(advStats,"rar:([\\d]+(?: K)?)")));
        audioStats.setBitRateTx(parseInt(regexFind(advStats,"tar:([\\d]+(?: K)?)")));
        audioStats.setCodec(regexFind(netStats,"tap:([^ ]+)"));
        audioStats.setJitterRx(parseFloat(regexFind(advStats,"raj:([\\d.]+)")));
        audioStats.setJitterTx(parseFloat(regexFind(advStats,"taj:([\\d.]+)")));
        audioStats.setPacketLossRx(parseInt(regexFind(advStats,"rapl:([\\d]+(?: K)?)")));
        audioStats.setPacketLossTx(parseInt(regexFind(advStats,"tapl:([\\d]+(?: K)?)")));
        audioStats.setMuteTx(send(queryNearAudioMute).contains("on"));
        return audioStats;
    }

    private Map<String, String> getSystemStatistics(String status,float volume) throws Exception {
        Map<String,String> stats = new HashMap<>();
        stats.put("uptime",send(queryUptime));
        final String deviceInfo = send(queryDeviceInfo);
        stats.put("deviceName",regexFind(deviceInfo,"name is : *([^\\n\\r]+)"));
        stats.put("deviceModel",regexFind(deviceInfo,"Model: *([^\\n\\r]+)"));
        stats.put("serialNumber",regexFind(deviceInfo,"Serial Number: *([^\\n\\r]+)"));
        stats.put("softwareVersion",regexFind(deviceInfo,"Software Version: *([^\\n\\r]+)"));
        stats.put("buildInfo",regexFind(deviceInfo,"Build Information: *([^\\n\\r]+)"));
        stats.put("timeInLastCall",regexFind(deviceInfo,"Time In Last Call: *([^\\n\\r]+)"));
        stats.put("totalCallTime",regexFind(deviceInfo,"Total Time In Calls: *([^\\n\\r]+)"));
        stats.put("totalCalls",regexFind(deviceInfo,"Total Calls: *([^\\n\\r]+)"));
        stats.put("deviceTime",regexFind(deviceInfo,"Local Time is: *([^\\n\\r]+)"));
        stats.put("microphoneState",regexFind(status,"microphones (online|offline)"));
        stats.put("globalDirectory",regexFind(status,"globaldirectory (online|offline)"));
        if (send(queryCalendarEnabled).contains("yes")){ //If the calendar resource isn't empty
            stats.put("calendarService",regexFind(status,"calendar (online|offline)"));
            stats.put("calendarResource",send(queryCalendarResource).replace("calendarresource ","").replaceAll("\"",""));
            stats.put("calendarServer",send(queryCalendarServer).replace("calendarserver ","").replaceAll("\"",""));
            stats.put("calendarStatus",send(queryCalendarStatus).replace("calendarstatus",""));
        }

        //Statistics for control properties
        stats.put("volume",String.valueOf(volume));
        stats.put("wake","0");
        stats.put("reboot","0");
        return stats;
    }

    private CallStats getCallStats(String netStats){
        CallStats callStats = new CallStats();

        final String tx = regexFind(netStats,"txrate:([\\d]+(?: K)?)");
        callStats.setCallRateTx(parseInt(tx));

        final String rx = regexFind(netStats,"rxrate:([\\d]+(?: K)?)");
        callStats.setCallRateRx(parseInt(rx));

        final String callId = regexFind(netStats,"call:([^ ]+)");
        if (!callId.isEmpty()) {
            callStats.setCallId(callId);
        }

        callStats.setPercentPacketLossTx(Float.parseFloat(regexFind(netStats,"%pktloss:([\\d.]+)%")));
        callStats.setProtocol(regexFind(netStats,"tcp:([^ ]+)"));
        callStats.setTotalPacketLossTx(Integer.valueOf(regexFind(netStats,"pktloss:([\\d]+)")));
        return callStats;
    }

    private RegistrationStatus getRegistrationStatus(String status) throws Exception {
        RegistrationStatus registrationStatus = new RegistrationStatus();
        if (send(queryH323Enable).contains("true")){
            final String gateKeeperIP = send(queryGatekeeperIp).replace("gatekeeperip ","");
            if (!gateKeeperIP.contains("\"\"")){
                registrationStatus.setH323Gatekeeper(gateKeeperIP);
            } else {
                registrationStatus.setH323Gatekeeper("");
            }
            registrationStatus.setH323Registered(regexFind(status,"gatekeeper (online|offline)").equals("online"));
        } else {
            registrationStatus.setH323Registered(false);
            registrationStatus.setH323Details("H.323 is disabled.");
        }

        if (send(querySipEnable).contains("true")){
            final String sipRegistrar = send(querySipRegistrar).replace("systemsetting sipregistrarserver ","");
            if (!sipRegistrar.contains("\"\"")){
                registrationStatus.setSipRegistrar(sipRegistrar);
            } else {
                registrationStatus.setSipRegistrar("");
            }
            registrationStatus.setSipRegistered(regexFind(status,"sipserver (online|offline)").equals("online"));
        } else {
            registrationStatus.setSipRegistered(false);
            registrationStatus.setSipDetails("SIP is disabled.");
        }
        return registrationStatus;
    }

    private boolean getInCall() throws Exception {
        final String callInfo = send(queryCallInfo);
        return !callInfo.equals("system is not in a call");
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        switch (cp.getProperty()){
            case "reboot":
                send(cmdReboot);
                break;
            case "wake":
                send(cmdWake);
                break;
            case "volume":
                send(cmdVolume + round((float) cp.getValue()));
                break;
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for(ControllableProperty cp : list){
            if (cp != null)
                controlProperty(cp);
        }
    }

    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }
    private int parseInt(String string){
         return Integer.parseInt(string.replace("K","000").replaceAll("\\s",""));
    }
    private float parseFloat(String string){
        return Float.parseFloat(string.replaceAll("[\\s\\r\\n\\w]+",""));
    }

    public static void main(String[] args) throws Exception {
        x30 device = new x30();
        device.setHost("192.168.0.76");
        device.init();
        device.getMultipleStatistics();
    }
}
