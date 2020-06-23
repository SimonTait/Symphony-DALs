package com.insightsystems.dal.crestron;

import com.avispl.symphony.api.common.error.NotAuthorizedException;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.insightsystems.dal.crestron.JsonUtils.parseJson;
import static java.util.Collections.singletonList;

/************************ChangeLog*****************
 **** v1.0
 * - Initial version
 *
 **** v1.1
 *  - Added custom communicator to deal with 301 response code (which means login successful)
 *  - Added default login credentials
 *  - Added NotAuthorisedException on incorrect credentials
 *  - Largely increased the number of statistics being monitored
 *  - Updated controls to Symphony v4.9.1 standard

 */

public class AirMedia extends AM300HttpCommunicator implements Monitorable, Pingable, Controller {

    public AirMedia(){
        this.setLogin("admin");
        this.setPassword("admin");
    }

    private void authenticate(){
        try
        {
            doPost("/userlogin.html","login=" + (!getLogin().isEmpty() ? getLogin() : "admin") + "&passwd=" + (!getPassword().isEmpty() ? getPassword() : "admin"));
        }catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        Map<String,String> deviceStatistics = new HashMap<>();
        ExtendedStatistics statistics = new ExtendedStatistics();

        HttpResponse devResponse = doGet("Device/");
        if (devResponse.getResponseCode() != 200){
            authenticate();
            devResponse = doGet("Device/");
            if (devResponse.getResponseCode() == 403){
                throw new NotAuthorizedException("Login failed, Username or password incorrect.");
            }
        }
        System.out.println(devResponse.getFullResponse());

        JsonNode json = parseJson(devResponse.getResponseBody());

        deviceStatistics.put("pufVersion", json.at("/Device/DeviceInfo/PufVersion").asText());
        deviceStatistics.put("serialNumber",json.at("/Device/DeviceInfo/SerialNumber").asText());
        deviceStatistics.put("macAddress",json.at("/Device/DeviceInfo/MacAddress").asText());
        deviceStatistics.put("model",json.at("/Device/DeviceInfo/Model").asText());
        deviceStatistics.put("rebootReason",json.at("/Device/DeviceInfo/RebootReason").asText());
        deviceStatistics.put("uptime",json.at("/Device/DeviceSpecific/UpTime").asText().replace("The system has been running for ",""));
        deviceStatistics.put("autoRoutingEnabled",json.at("/Device/SourceSelectionConfiguration/IsAutoRoutingEnabled").asText());
        deviceStatistics.put("miracastEnabled",json.at("/Device/AirMedia/Miracast/IsEnabled").asText());
        deviceStatistics.put("autoUpdateEnabled",json.at("/Device/AutoUpdateMaster/IsEnabled").asText());
        deviceStatistics.put("airmediaEnabled",json.at("/Device/AirMedia/IsEnabled").asText());
        if (json.at("/Device/AirMedia/Miracast/IsEnabled").asText().equals("true")){
            deviceStatistics.put("miracastDongleStatus",json.at("/Device/AirMedia/Miracast/WifiDongleStatus").asText());
        }


        final ArrayNode inputs = (ArrayNode) json.at("/Device/AudioVideoInputOutput/Inputs");
        int hdmiCount = 1;
        for (int i = 0; i < inputs.size(); i++){
            JsonNode port = inputs.get(i).at("/Ports").get(0);
            String inputName = port.at("/PortType").asText().equalsIgnoreCase("HDMI") ? "hdmi" + hdmiCount++ + "Input": port.at("/PortType").asText() + "Input";
            deviceStatistics.put(inputName + "SourceDetected",port.at("/IsSourceDetected").asText());
            deviceStatistics.put(inputName + "SyncDetected",port.at("/IsSyncDetected").asText());
            deviceStatistics.put(inputName + "Resolution",port.at("/HorizontalResolution").asText() + "x" + port.at("/VerticalResolution").asText() + "@" + port.at("/FramesPerSecond").asText() + " " + port.at("/Audio/Digital/Format").asText());
            deviceStatistics.put(inputName + "HdcpSupportEnabled",port.at("/Hdmi/IsHdcpSupportEnabled").asText());
            deviceStatistics.put(inputName + "SourceHdcpActive",port.at("/Hdmi/IsSourceHdcpActive").asText());
            deviceStatistics.put(inputName + "HdcpState",port.at("/Hdmi/HdcpState").asText());
        }
        final ArrayNode outputs = (ArrayNode) json.at("/Device/AudioVideoInputOutput/Outputs");
        hdmiCount = 1;
        for (int i = 0; i < outputs.size(); i++){
            JsonNode port = outputs.get(i).at("/Ports").get(0);
            String outputName = port.at("/PortType").asText().equalsIgnoreCase("HDMI") ? "hdmi" + hdmiCount++ + "Output" : port.at("/PortType").asText() + "Output";
            if (port.at("/Hdmi/IsOutputDisabled").asText().equals("true")){
                deviceStatistics.put(outputName + "Disabled","true");
            }else {

                deviceStatistics.put(outputName + "SinkConnected", port.at("/IsSinkConnected").asText());
                deviceStatistics.put(outputName + "HdcpForceDisabled", port.at("/Hdmi/IsHdcpForceDisabled").asText());
                deviceStatistics.put(outputName + "Resolution", port.at("/HorizontalResolution").asText() + "x" + port.at("/VerticalResolution").asText() + "@" + port.at("/FramesPerSecond").asText() + " " + port.at("/Audio/Digital/Format").asText());
                deviceStatistics.put(outputName + "DisabledByHdcp", port.at("/Hdmi/DisabledByHdcp").asText());
                deviceStatistics.put(outputName + "HdcpTransmitterMode", port.at("/Hdmi/HdcpTransmitterMode").asText());
                deviceStatistics.put(outputName + "HdcpState", port.at("/Hdmi/HdcpState").asText());
            }
        }

        deviceStatistics.put("reboot","0");

        //Create advanced Controls
        AdvancedControllableProperty.Button rebootButton = new AdvancedControllableProperty.Button();
        rebootButton.setGracePeriod(10000L);
        rebootButton.setLabel("Reboot");
        rebootButton.setLabelPressed("Rebooting...");

        statistics.setStatistics(deviceStatistics);
        statistics.setControllableProperties(singletonList(new AdvancedControllableProperty("reboot",new Date(),rebootButton,"0")));
        return singletonList(statistics);
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        if (controllableProperty == null)
            return;

        if (controllableProperty.getProperty().equalsIgnoreCase("reboot")) {
            HttpResponse response = doPost("Device/DeviceOperations", "{\"Device\":{\"DeviceOperations\":{\"Reboot\":true}}}"); //send reboot command to the devices

            if ((!response.getResponseBody().contains("OK") || response.getResponseCode() != 200) && this.logger.isErrorEnabled()){
                this.logger.error("Device Reboot failed with response from device: " + response);
            }
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty controllableProperty : list)
            controlProperty(controllableProperty);
    }

    public static void main(String[] args) throws Exception {
        AirMedia am = new AirMedia();
        am.setHost("192.168.0.112");
        am.setPassword("19881988");
        am.init();
        ((ExtendedStatistics)am.getMultipleStatistics().get(0)).getStatistics().forEach((k,v) -> System.out.println(k + " : " + v));

    }
}
