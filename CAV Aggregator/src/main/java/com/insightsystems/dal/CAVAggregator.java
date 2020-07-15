package com.insightsystems.dal;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;

import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

public class CAVAggregator extends RestCommunicator implements Aggregator, Pingable {
    private ObjectMapper objectMapper;
    private List<AggregatedDevice> aggregatedDevices;

    public CAVAggregator(){
        objectMapper = new ObjectMapper();
        aggregatedDevices = new ArrayList<>();
    }

    @Override
    protected void authenticate() throws Exception {

    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
        final ArrayNode deviceArray = (ArrayNode) objectMapper.readTree(this.doGet(""));
        for (JsonNode device : deviceArray){
            aggregatedDevices.add(createDevice(device));
        }
        return aggregatedDevices;
    }


    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
        return retrieveMultipleStatistics().stream()
                .filter(list::contains)
                .collect(Collectors.toList());
    }

    private AggregatedDevice createDevice(JsonNode deviceJson){
        AggregatedDevice device = new AggregatedDevice();
        device.setDeviceName(deviceJson.at("/deviceName").asText(""));
        device.setDeviceId(deviceJson.at("/deviceId").asText(""));
        device.setDeviceMake(deviceJson.at("/deviceMake").asText(""));
        device.setDeviceModel(deviceJson.at("/deviceModel").asText(""));
        device.setDeviceType(deviceJson.at("/deviceType").asText(""));
        device.setDeviceOnline(deviceJson.at("/deviceOnline").asBoolean(false));
        device.setSerialNumber(deviceJson.at("/serialNumber").asText(""));
        if (deviceJson.has("statistics")) {
            final JsonNode statistics = deviceJson.at("/statistics");
            Map<String,String> deviceStatistics = new HashMap<>();
            for (Iterator<String> it = statistics.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                deviceStatistics.put(field,statistics.at("/"+field).asText(""));
            }
            device.setStatistics(deviceStatistics);
        }

        if (deviceJson.has("properties")) {
            final JsonNode properties = deviceJson.at("/properties");
            Map<String,String> deviceProperties = new HashMap<>();
            for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (field.equals("macAddress")){
                    device.setMacAddresses(Collections.singletonList(properties.at("/" + field).asText("")));
                }else {
                    deviceProperties.put(field, properties.at("/" + field).asText(""));
                }
            }
            device.setProperties(deviceProperties);
        }
        device.setTimestamp(System.currentTimeMillis());
        return device;
    }

    public static void main(String[] args) throws Exception {
        CAVAggregator test = new CAVAggregator();
        test.setHost("avcoprdws01.rmit.internal");
        test.setPort(8080);
        test.init();
        List<AggregatedDevice> devices = test.retrieveMultipleStatistics();
        for (AggregatedDevice dev : devices){
            System.out.println(dev.getDeviceName() + " : " + dev.getDeviceId());
        }
    }
}
