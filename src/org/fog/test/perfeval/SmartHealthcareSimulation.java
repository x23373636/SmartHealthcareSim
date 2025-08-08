/*
 * SmartHealthcareSimulation.java
 * A fog-based healthcare system simulation using iFogSim and CloudSim.
 * Demonstrates data generation by patient sensors, fog node processing,
 * energy tracking, and cloud data offloading with EQLS-based load balancing.
 */

package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;
import java.util.*;

public class SmartHealthcareSimulation {

    public static void main(String[] args) {
        Log.printLine("Starting Smart Healthcare Simulation...");

        try {
            // Initialize CloudSim with 1 user, current time, no trace events
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create a cloud storage entity for storing processed results
            CloudStorage cloud = new CloudStorage("CloudDataCenter");

            // Create 3 fog nodes and connect them to the cloud
            FogNode fog1 = new FogNode("FogNode1", cloud);
            FogNode fog2 = new FogNode("FogNode2", cloud);
            FogNode fog3 = new FogNode("FogNode3", cloud);
            FogNode[] fogNodes = {fog1, fog2, fog3};

            // Create an EQLS load balancer responsible for distributing tasks to fog nodes
            EQLSLoadBalancer proxy = new EQLSLoadBalancer("EQLSBalancer", fogNodes);

            // Create 4 patient sensors with critical and non-critical settings
            PatientSensor[] sensors = {
                new PatientSensor("Sensor1", true),
                new PatientSensor("Sensor2", false),
                new PatientSensor("Sensor3", true),
                new PatientSensor("Sensor4", false)
            };

            // Initialize task scheduler to handle sensor data generation and scheduling
            TaskScheduler scheduler = new TaskScheduler(sensors, proxy);
            scheduler.scheduleTasks();

            // Start and stop the simulation
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Print simulation results to the console
            Log.printLine("Simulation Completed.");
            Log.printLine("Cloud Storage Used: " + cloud.getStorageUsed() + " MB");
            for (FogNode node : fogNodes) {
                Log.printLine(node.getName() + " Energy Used: " + node.getEnergyConsumed() + " J");
            }
            Log.printLine("EQLSBalancer Energy Used: " + proxy.getEnergyConsumed() + " J");

            // Output performance metrics for each sensor
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

    // Class to simulate a wearable patient sensor device
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

        // Generate health data and calculate energy usage
        public void generateData() {
            double dataMB = 2.0; // 2 MB data per sensor
            double energyPerMB = 0.02; // 0.02 J/MB for transmission
            dataGenerated += dataMB;
            energyUsed += dataMB * energyPerMB;
        }

        // Store latency value for this sensor
        public void recordLatency(double latencyValue) {
            latency = latencyValue;
        }

        public double getEnergyUsed() { return energyUsed; }
        public double getDataGenerated() { return dataGenerated; }
        public double getLatency() { return latency; }
    }

    // Fog node entity responsible for processing data and forwarding results to the cloud
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
        public void processEvent(SimEvent ev) {
            // No events to handle directly
        }

        // Process data from a sensor and forward partial result to cloud
        public void processData(PatientSensor sensor) {
            double dataMB = 2.0;
            double resultMB = 0.5; // Data sent to cloud
            double energyPerMB = 0.03; // 0.03 J/MB for fog processing

            energyConsumed += dataMB * energyPerMB;
            currentLoad++;

            Log.printLine(getName() + " processed data from " + sensor.getName());
            cloud.storeData(resultMB);
        }

        public double getEnergyConsumed() { return energyConsumed; }
        public int getCurrentLoad() { return currentLoad; }
    }

    // Cloud storage entity that accumulates processed results
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

        // Store incoming result data
        public void storeData(double mb) {
            storageUsed += mb;
        }

        public double getStorageUsed() { return storageUsed; }
    }

    // EQLS Load Balancer: selects the least loaded fog node for processing
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

        // Process incoming sensor task and assign to best fog node
        @Override
        public void processEvent(SimEvent ev) {
            if (ev.getTag() == 1001 && ev.getData() instanceof Object[]) {
                Object[] data = (Object[]) ev.getData();
                PatientSensor sensor = (PatientSensor) data[0];
                double sendTime = (double) data[1];
                forwardToFog(sensor, sendTime);
            }
        }

        // Determine the best fog node and forward the sensor task
        private void forwardToFog(PatientSensor sensor, double sendTime) {
            FogNode bestNode = Arrays.stream(fogNodes)
                .min(Comparator.comparingInt(FogNode::getCurrentLoad))
                .orElse(fogNodes[0]);

            double receiveTime = CloudSim.clock();
            double latency = receiveTime - sendTime;

            sensor.recordLatency(latency);
            bestNode.processData(sensor);
            energyConsumed += 0.01; // Fixed energy usage per task

            Log.printLine(getName() + " forwarded " + sensor.getName() +
                " to " + bestNode.getName() + " (Latency: " + latency + ")");
        }

        public double getEnergyConsumed() { return energyConsumed; }
    }

    // Task scheduler to trigger sensor data generation and scheduling to the load balancer
    public static class TaskScheduler {
        private final PatientSensor[] sensors;
        private final EQLSLoadBalancer proxy;

        public TaskScheduler(PatientSensor[] sensors, EQLSLoadBalancer proxy) {
            this.sensors = sensors;
            this.proxy = proxy;
        }

        // Schedule tasks for each sensor to the load balancer
        public void scheduleTasks() {
            for (PatientSensor sensor : sensors) {
                sensor.generateData(); // Each sensor generates data
                double sendTime = CloudSim.clock(); // Record send time
                CloudSim.send(proxy.getId(), proxy.getId(), 0.1, 1001, new Object[]{sensor, sendTime});
            }
        }
    }
}
