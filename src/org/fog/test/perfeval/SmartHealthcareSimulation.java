package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;
import java.util.*;

public class SmartHealthcareSimulation {

    public static void main(String[] args) {
        Log.printLine("Starting Smart Healthcare Simulation...");

        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            CloudStorage cloud = new CloudStorage("CloudDataCenter");

            // Fog cluster with 3 fog nodes
            FogNode fog1 = new FogNode("FogNode1", cloud);
            FogNode fog2 = new FogNode("FogNode2", cloud);
            FogNode fog3 = new FogNode("FogNode3", cloud);
            FogNode[] fogNodes = {fog1, fog2, fog3};

            // Load balancer
            EQLSLoadBalancer proxy = new EQLSLoadBalancer("EQLSBalancer", fogNodes);

            // Patient sensors (IoT devices)
            PatientSensor[] sensors = {
                new PatientSensor("Sensor1", true),
                new PatientSensor("Sensor2", false),
                new PatientSensor("Sensor3", true),
                new PatientSensor("Sensor4", false)
            };

            TaskScheduler scheduler = new TaskScheduler(sensors, proxy);
            scheduler.scheduleTasks();

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Simulation Completed.");
            Log.printLine("Cloud Storage Used: " + cloud.getStorageUsed() + " MB");
            for (FogNode node : fogNodes) {
                Log.printLine(node.getName() + " Energy Used: " + node.getEnergyConsumed() + " J");
            }
            Log.printLine("EQLSBalancer Energy Used: " + proxy.getEnergyConsumed() + " J");

            // Output sensor metrics
            for (PatientSensor sensor : sensors) {
                Log.printLine(sensor.getName() +
                    " - Data Generated: " + sensor.getDataGenerated() + " MB, " +
                    "Energy Used: " + sensor.getEnergyUsed() + " J, " +
                    "Latency: " + sensor.getLatency() + " sec");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class PatientSensor {
        private final String name;
        private final boolean critical;
        private double energyUsed = 0.0;
        private double dataGenerated = 0.0;
        private double latency = 0.0;

        public PatientSensor(String name, boolean critical) {
            this.name = name;
            this.critical = critical;
        }

        public String getName() { return name; }
        public boolean isCritical() { return critical; }

        public void generateData() {
            double dataMB = 2.0;
            double energyPerMB = 0.02;
            dataGenerated += dataMB;
            energyUsed += dataMB * energyPerMB;
        }

        public void recordLatency(double latencyValue) {
            latency = latencyValue;
        }

        public double getEnergyUsed() { return energyUsed; }
        public double getDataGenerated() { return dataGenerated; }
        public double getLatency() { return latency; }
    }

    public static class FogNode extends SimEntity {
        private double energyConsumed = 0.0;
        private final CloudStorage cloud;
        private int currentLoad = 0;

        public FogNode(String name, CloudStorage cloud) {
            super(name);
            this.cloud = cloud;
        }

        @Override
        public void startEntity() {
            Log.printLine(getName() + " is starting...");
        }

        @Override
        public void shutdownEntity() {
            Log.printLine(getName() + " is shutting down...");
        }

        @Override
        public void processEvent(SimEvent ev) {}

        public void processData(PatientSensor sensor) {
            double dataMB = 2.0;
            double resultMB = 0.5;
            double energyPerMB = 0.03;

            energyConsumed += dataMB * energyPerMB;
            currentLoad++;

            Log.printLine(getName() + " processed data from " + sensor.getName());
            cloud.storeData(resultMB);
        }

        public double getEnergyConsumed() { return energyConsumed; }
        public int getCurrentLoad() { return currentLoad; }
    }

    public static class CloudStorage extends SimEntity {
        private double storageUsed = 0.0;

        public CloudStorage(String name) {
            super(name);
        }

        @Override
        public void startEntity() {
            Log.printLine(getName() + " is starting...");
        }

        @Override
        public void shutdownEntity() {
            Log.printLine(getName() + " is shutting down...");
        }

        @Override
        public void processEvent(SimEvent ev) {}

        public void storeData(double mb) {
            storageUsed += mb;
        }

        public double getStorageUsed() { return storageUsed; }
    }

    public static class EQLSLoadBalancer extends SimEntity {
        private final FogNode[] fogNodes;
        private double energyConsumed = 0.0;

        public EQLSLoadBalancer(String name, FogNode[] fogNodes) {
            super(name);
            this.fogNodes = fogNodes;
        }

        @Override
        public void startEntity() {
            Log.printLine(getName() + " is starting...");
        }

        @Override
        public void shutdownEntity() {
            Log.printLine(getName() + " is shutting down...");
        }

        @Override
        public void processEvent(SimEvent ev) {
            if (ev.getTag() == 1001 && ev.getData() instanceof Object[]) {
                Object[] data = (Object[]) ev.getData();
                PatientSensor sensor = (PatientSensor) data[0];
                double sendTime = (double) data[1];
                forwardToFog(sensor, sendTime);
            }
        }

        private void forwardToFog(PatientSensor sensor, double sendTime) {
            FogNode bestNode = Arrays.stream(fogNodes)
                .min(Comparator.comparingInt(FogNode::getCurrentLoad))
                .orElse(fogNodes[0]);

            double receiveTime = CloudSim.clock();
            double latency = receiveTime - sendTime;

            sensor.recordLatency(latency);
            bestNode.processData(sensor);
            energyConsumed += 0.01;

            Log.printLine(getName() + " forwarded " + sensor.getName() +
                " to " + bestNode.getName() + " (Latency: " + latency + ")");
        }

        public double getEnergyConsumed() { return energyConsumed; }
    }

    public static class TaskScheduler {
        private final PatientSensor[] sensors;
        private final EQLSLoadBalancer proxy;

        public TaskScheduler(PatientSensor[] sensors, EQLSLoadBalancer proxy) {
            this.sensors = sensors;
            this.proxy = proxy;
        }

        public void scheduleTasks() {
            for (PatientSensor sensor : sensors) {
                sensor.generateData();
                double sendTime = CloudSim.clock();
                CloudSim.send(proxy.getId(), proxy.getId(), 0.1, 1001, new Object[]{sensor, sendTime});
            }
        }
    }
}

