package com.insightsystems.dal.dataprobe;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.security.auth.login.LoginException;
import java.util.*;

public class iBootPDU extends RestCommunicator implements Controller, Pingable, Monitorable {
    private String accessToken = "";
    private ObjectMapper objectMapper;
    private String[] outletNames;

    @Override
    protected void authenticate() throws Exception {
        final JsonNode authResponse = objectMapper.readTree(this.doPost("/services/auth","{\"username\":\"" + this.getLogin() + "\", \"password\":\"" + this.getPassword() + "\"}"));
        if (authResponse.at("/success").asBoolean()){
            accessToken = authResponse.at("/token").asText();
        } else {
            throw new LoginException(authResponse.at("/message").asText());
        }
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();
        List<AdvancedControllableProperty> controls = new ArrayList<>();
        if (accessToken.isEmpty()){
            authenticate();
        }

        final JsonNode pduState = objectMapper.readTree(this.doPost("/services/retrieve",
                "{" +
                "\"token\":\""+ accessToken +"\", " +
                "\"outlets\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\"], " +
                "\"groups\":[], " +
                "\"names\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\"]" +
                "}"));

        for (int i = 0; i < 8; i++){
            final String outletState = pduState.at("/outlets/" + (i+1)).asText() == "On" ? "1" : "0";
            outletNames[i] = pduState.at("/names/"+ (i+1)).asText();
            stats.put(outletNames[i],outletState);
            controls.add(new AdvancedControllableProperty(outletNames[i],new Date(),new AdvancedControllableProperty.Switch(){{setLabelOn("On");setLabelOff("Off");}},outletState));
        }
        extendedStatistics.setStatistics(stats);
        return Collections.singletonList((Statistics)extendedStatistics);
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {

    }
}
