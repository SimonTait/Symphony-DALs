package com.insightsystems.dal.epson;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;

import java.util.*;

public class EpsonProjector extends EpsonTcpSocketCommunicator implements Pingable, Monitorable, Controller {
    private static final String ESC = "\u001b", CR = "\r",LF="\n",CRLF="\r\n",NULL="\u0000";
    private static final String[] errorCodes = {"00","01","03","04","06","07","08","09","0A","0B","0C","0D","0E","0F","10","11","12","13","14","15","16","17","18"};
    private static final String[] errorMessages = {"None","Fan Error","Lamp Failure at Power On","High Internal Temperature Error","Lamp Error","Open Lamp Cover Door Error","Cinema Filter Error","Electric Dual-Layered Capacitor is Disconnected","Auto Iris Error","Subsystem Error","Low Air Flow Error","Air Filter Air Flow Sensor Error","Power Supply Unit Error","Shutter Error","Cooling System Error (Peltiert Element)","Cooling System Error (Pump)","Static Iris Error","Power Supply Unit Error (Disagreement of Ballast)","Exhaust Shutter Error","Obstacle Detection Error","IF Board Discernment Error","Communication Error \"Stack Projection Function\"","IC2 Error"};
    private final String queryError = "ERR?",queryPower = "PWR?",queryLamp = "LAMP?", querySource = "SOURCE?",queryVolume = "VOL?", queryAVMute = "MUTE?",querySerial="SNO?",querySignal="SIGNAL?";
    private final String[][] powerEnum = {{"00","Standby Mode (Network OFF)","0"},{"01","Lamp ON","1"},{"02","Warmup","1"},{"03","Cooldown","0"},{"04","Standby Mode (Network ON)","0"},{"05","Abnormality Standby","0"},{"09","A/V Standby","0"}};
    private final String[][] signalEnum = {{"00","No Signal"},{"01","Signal Detected"},{"FF","Unsupported Signal"}};
    private final String[] sourceEnum = {"11","B1","B4","41","42","30","52","53","80","A0"};
    private final String[] sourceFriendlyEnum = {"Computer 1 VGA", "Computer 2 BNC","Component","Video 1","S-Video","HDMI","USB","LAN","HDbaseT","DVI"};

    private final List<AdvancedControllableProperty> controls = new ArrayList<>();

    public EpsonProjector() {
        this.setCommandErrorList(Collections.singletonList(""));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setLoginSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList(""));
        this.setPassword("");
        this.setLogin("");
        this.setPort(3629);

        AdvancedControllableProperty.Slider volumeSlider = new AdvancedControllableProperty.Slider();
        volumeSlider.setLabelStart("0");
        volumeSlider.setLabelEnd("255");
        volumeSlider.setRangeStart(0F);
        volumeSlider.setRangeEnd(255F);
        controls.add(new AdvancedControllableProperty("volume", new Date(), volumeSlider, 0F));

        AdvancedControllableProperty.Switch powerSwitch = new AdvancedControllableProperty.Switch();
        powerSwitch.setLabelOff("Off");
        powerSwitch.setLabelOn("On");
        controls.add(new AdvancedControllableProperty("power", new Date(), powerSwitch, powerEnum[4][2]));

        AdvancedControllableProperty.Switch muteSwitch = new AdvancedControllableProperty.Switch();
        muteSwitch.setLabelOff("Off");
        muteSwitch.setLabelOn("On");
        controls.add(new AdvancedControllableProperty("pictureMute", new Date(), muteSwitch, false));

        AdvancedControllableProperty.DropDown SelectedInputDropdown = new AdvancedControllableProperty.DropDown();
        SelectedInputDropdown.setOptions(sourceFriendlyEnum);
        SelectedInputDropdown.setLabels(sourceFriendlyEnum);
        controls.add(new AdvancedControllableProperty("selectedInput", new Date(), SelectedInputDropdown, "HDMI"));
    }

    @Override
    protected void internalInit() throws Exception {
        this.setPort(3629);
        super.internalInit();
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        final String value = (String) cp.getValue();
        switch (cp.getProperty()){
            case "selectedInput":
                for (int i = 0;i<sourceFriendlyEnum.length;i++){
                    if (value.equalsIgnoreCase(sourceFriendlyEnum[i])){
                        send("SOURCE " + sourceEnum[i]); //Send the source command for corresponding source to proj
                    }
                }
                break;
            case "pictureMute":
                if (value.equals("1") || value.equalsIgnoreCase("true")){
                    send("MUTE ON");
                } else {
                    send("MUTE OFF");
                }
                break;
            case "power":
                if (value.equals("1") || value.equalsIgnoreCase("true")){
                    send("PWR ON");
                } else {
                    send("PWR OFF");
                }
                break;
            case "volume":
                send("VOL " + value);
                break;
            default:
                throw new Exception("Could not find control property +" + cp.getProperty());
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty cp : list){
            controlProperty(cp);
        }
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();

        stats.put("serialNumber",send(querySerial).replace("SNO=",""));
        stats.put("volume",send(queryVolume).replace("VOL=",""));
        stats.put("epson.g7500.lampHours",send(queryLamp).replace("LAMP=",""));

        final String selectedInput = send(querySource);
        for (int i= 0;i<sourceEnum.length;i++){
            if (selectedInput.contains(sourceEnum[i])){
                stats.put("selectedInput",sourceFriendlyEnum[i]);
                break;
            }
        }

        final String powerState = send(queryPower);
        for (String[] strings : powerEnum) {
            if (powerState.contains(strings[0])) {
                stats.put("powerState", strings[1]); //Output the description of the return power state;
                stats.put("power", strings[2]); //Simple on/off state for control property
                break;
            }
        }

        final String signalDetected = send(querySignal);
        for (String[] string :signalEnum){
            if (signalDetected.contains(string[0])){
                stats.put("signalState",string[1]);
                break;
            }
        }

        final String errorState = send(queryError);
        for (int i = 0; i< errorCodes.length;i++){
            if (errorState.contains(errorCodes[i])){
                stats.put("error",errorMessages[i]);
                break;
            }
        }

        final String muteState = send(queryAVMute);
        if (muteState.contains("ON"))
            stats.put("pictureMute","true");
        else if (muteState.contains("OFF"))
            stats.put("pictureMute","false");

        extStats.setControllableProperties(controls);
        extStats.setStatistics(stats);
        return Collections.singletonList(extStats);
    }

    public static void main(String[] args) throws Exception {
        EpsonProjector test = new EpsonProjector();
        test.setHost("10.204.66.23");
        test.init();
        ((ExtendedStatistics)test.getMultipleStatistics().get(0)).getStatistics().forEach((k,v)->System.out.println(k + " : " + v));

    }
}
