package com.insightsystems.dal.crestron;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.TelnetCommunicator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DMFrame extends TelnetCommunicator implements Pingable, Monitorable, Controller {
    private final String queryRoute = "DUMPDMROUTEI", queryStatus = "estatus", queryDmReport = "reportdm";


    public DMFrame(){
        this.setCommandErrorList(Collections.singletonList("None"));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList("None"));
        this.setLoginSuccessList(Collections.singletonList(""));
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        send(""); //Send newline in case there is another client connected (a command is preloaded and anything send is appended to the end)
        Map<String, String> stats = new LinkedHashMap<String,String>();
        List<AdvancedControllableProperty> ctrls = new ArrayList<>();
        final String status = send(queryStatus);
        stats.put("macAddress",regexFind(status,"MAC Address\\(es\\):[\\s]+([\\w.:;]+)"));
        stats.put("hostname",regexFind(status,"Host Name:[\\s]+([\\S.]+)"));
        stats.put("Dhcp",regexFind(status,"DHCP:[\\s]+([\\S.]+)"));


        final String[] slots = send(queryRoute).split(" Routing Information");
        List<String> inputs = new ArrayList<>();
        for (String slot : slots){
            if (slot.contains("Card at Slot")){
                final String[] lines = slot.split("\r\n");
                String slotNumber;
                if (!(slotNumber = regexFind(lines[0],"Input Card at [sS]lot (\\d+)")).isEmpty()){
                    inputs.add(slotNumber);
                } else if (!(slotNumber = regexFind(lines[0],"Output Card at [Ss]lot (\\d+)")).isEmpty()){
                    slotNumber = String.valueOf((Integer.parseInt(slotNumber) - 16));
                    for (String line : lines){
                        String inputSlot = regexFind(line,"Input Card at [sS]lot (\\d+)");
                        String type;
                        if (!(type = regexFind(line,"(Video|Audio|USB)")).isEmpty()){
                            if (inputSlot.isEmpty()){ //Route is blank
                                stats.put("Output" + slotNumber + type, "0");
                                ctrls.add(createDropdown("Output" + slotNumber + type, inputs,"0"));
                            } else {
                                stats.put("Output" + slotNumber + type, inputSlot);
                                ctrls.add(createDropdown("Output" + slotNumber + type, inputs,inputSlot));
                            }
                        }
                    }
                }
            }
        }
        extStats.setControllableProperties(ctrls);
        extStats.setStatistics(stats);
        return Collections.singletonList(extStats);
    }

    private AdvancedControllableProperty createDropdown(String name, List<String> inputs, String selectedInput) {
        if (!inputs.get(0).equals("0")) {
            inputs.add(0, "0"); //Add a no route option
        }
        String[] inputArr = inputs.toArray(new String[0]);
        AdvancedControllableProperty.DropDown dropdown = new AdvancedControllableProperty.DropDown();
        dropdown.setOptions(inputArr.clone());
        for (int i = 0; i < inputArr.length;i++){
            if (inputArr[i].equals("0"))
                inputArr[i] = "- Blank -";
            else
                inputArr[i] = "Input " + inputArr[i];
        }
        dropdown.setLabels(inputArr);
        return new AdvancedControllableProperty(name,new Date(),dropdown,selectedInput);
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {

    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {

    }


    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }


    public static void main(String[] args) throws Exception {
        DMFrame dm = new DMFrame();
        dm.setHost("10.193.77.175");
        dm.setPort(41795);
        dm.init();
        ((ExtendedStatistics)dm.getMultipleStatistics().get(0)).getStatistics().forEach((k,v)->System.out.println(k + " : " + v));

    }


}
