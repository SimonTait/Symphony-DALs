package com.insightsystems.dal;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.SshCommunicator;
import org.apache.log4j.BasicConfigurator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class APC extends SshCommunicator implements Pingable, Monitorable, Controller {


    public APC(){
        this.setCommandErrorList(Collections.singletonList(""));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(Collections.singletonList(""));
        this.setLoginSuccessList(Collections.singletonList(""));
        this.setPort(22);
        this.setLogin("apc");
        this.setPassword("apc");// <space>-c ?
        //this.setPassword("Password : ");
        BasicConfigurator.configure();
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        Map<String,String> stats = new HashMap<>();

        System.out.println(send("prodInfo"));
        return null;
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {

    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {

    }

    public static void main(String[] args) throws Exception {
        APC test = new APC();
        test.setHost("192.168.0.76");
        test.init();
        test.getMultipleStatistics();


    }
}
