package com.insightsystems.dal.extron;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.GenericStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.TelnetCommunicator;

import javax.security.auth.login.FailedLoginException;
import java.util.*;

/********
 *****v1.0
 * - Initial version
 *
 */

public class SMPRecorder extends TelnetCommunicator implements Monitorable, Pingable, Controller {
    private static final String ESC = "\u001b", CR = "\r",LF="\n",CRLF="\r\n";
    private static final String[] errorCodes = {"E10", "E12", "E13", "E14", "E17", "E18", "E22", "E24", "E26", "E28"};
    private static final String[] errorMessages = {"Unrecognized command","Invalid port number","Invalid parameter (number is out of range)","Not valid for this configuration","Invalid command for signal type","System timed out","Busy","Privilege violation","Maximum connections exceeded","Bad file name or file not found"};
    private static final String queryFirmware = "*Q", querySerial = "99I", queryMac="98I",queryCpuUsage = "11I",queryRes="33I",queryRecTime="35I",queryFrontPanelLock="X",queryRecStatus=ESC+"YRCDR" + LF,queryRecDestination=ESC+"DRCDR"+LF;
    private static final String commandRecCtrl= ESC+"Y'RCDR"+LF,commandFrontPanelLock="'X";
    private final String[] recDestinationEnum = {"Auto","Internal","UsbFront","UsbRear","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Unknown","Internal+Auto","Internal+UsbFront","Internal+UsbRear","Internal+UsbRcp"};
    private final List<AdvancedControllableProperty> controls;

    public SMPRecorder(){
        //BasicConfigurator.configure();
        this.setLoginSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList(""));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setCommandErrorList(Collections.singletonList(""));
        this.setPasswordPrompt("Password:");
        this.setPort(23);

        controls = new ArrayList<>();

        AdvancedControllableProperty.DropDown frontPanelLockDropdown = new AdvancedControllableProperty.DropDown();
        frontPanelLockDropdown.setLabels(new String[]{"Off","Complete Lockout","Menu Lockout","Allow Rec Ctrls Only"});
        frontPanelLockDropdown.setOptions(new String[]{"0","1","2","3"});
        controls.add(new AdvancedControllableProperty("frontPanelLock",new Date(),frontPanelLockDropdown,"0"));

        AdvancedControllableProperty.Preset recordStatePreset = new AdvancedControllableProperty.Preset();
        recordStatePreset.setLabels(new String[]{"Stop","Record","Pause"});
        recordStatePreset.setOptions(new String[]{"0","1","2"});
        controls.add(new AdvancedControllableProperty("recordState", new Date(),recordStatePreset,"0"));
    }

    @Override
    protected boolean doneReadingAfterConnect(String response) {
       System.out.println(response);
        if (response.contains("Login Administrator")){
            this.logger.info("Successfully logged in as Administrator");
        } else if (response.contains("Login User")) {
            this.logger.error("Login credentials are for a User account. Module will not have permission to control the device.");
        }
        return true;
    }


    @Override
    protected boolean doneReading(String command, String response) throws CommandFailureException {
        for (int i = 0; i < errorCodes.length; i++){
            if (response.contains(errorCodes[i])){
                //If the request contains an error code, throw exception with as much detail as we can
                this.logger.error("Error code: " + errorCodes[i] + " (" + errorMessages[i] + ") received for command: " + command);
            }
        }
        return true;
    }


    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        GenericStatistics genStats = new GenericStatistics();
        ExtendedStatistics extStats = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();

        genStats.setCpuPercentage(Float.valueOf(send(queryCpuUsage).trim()));
        stats.put("firmwareVersion",send(queryFirmware).trim());
        stats.put("serialNumber",send(querySerial).trim());
        stats.put("macAddress",send(queryMac).trim());
        stats.put("currentRecordDuration",send(queryRecTime).trim());
        stats.put("recordResolution",send(queryRes).trim());
        try {
            stats.put("recordDestination", recDestinationEnum[Integer.parseInt(send(queryRecDestination).trim())]);
        }catch(Exception ignored){}

        //Add in the current values for all controllable properties
        stats.put("frontPanelLock",send(queryFrontPanelLock).trim());
        stats.put("recordState",send(queryRecStatus).trim());

        extStats.setStatistics(stats);
        extStats.setControllableProperties(controls);
        return new ArrayList<Statistics>(){{add(genStats);add(extStats);}};
    }

    @Override
    public void controlProperty(ControllableProperty ctrlProp) throws Exception {
        if (ctrlProp.getProperty() == null || ctrlProp.getProperty().isEmpty() || ((String) ctrlProp.getValue()).isEmpty() || ctrlProp.getValue() == null)
            return;

        switch (ctrlProp.getProperty()){
            case "recordState":
                send(commandRecCtrl.replace("'",(String)ctrlProp.getValue()));
                System.out.println("recordState Property being controlled with value: " + ctrlProp.getValue());
                break;
            case "frontPanelLock":
                send(commandFrontPanelLock.replace("'",(String)ctrlProp.getValue()));
                System.out.println("frontPanelLock Property being controlled with value: " + ctrlProp.getValue());
                break;
            default:
                throw new Exception("Controllable property not found with name: " +  ctrlProp.getProperty());
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty cp : list){
            if (cp.getProperty() == null || cp.getProperty().isEmpty() || ((String) cp.getValue()).isEmpty() || cp.getValue() == null)
                continue;

            controlProperty(cp);
        }
    }

     public static void main(String[] args) throws Exception {
        SMPRecorder test = new SMPRecorder();
        test.setHost("10.152.66.22");
        test.setPassword("1988");
        test.init();
        ExtendedStatistics stats = (ExtendedStatistics)test.getMultipleStatistics().get(1);
        stats.getStatistics().forEach((k,v)->System.out.println(k + " : " + v));

     }
}