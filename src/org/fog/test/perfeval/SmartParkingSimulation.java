package org.fog.test.perfeval;

import java.util.*;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.*;

public class SmartParkingSimulation {

    public static void main(String[] args) {
        Log.printLine("Starting Smart Parking Simulation...");

        try {
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            // Initialize CloudSim
            CloudSim.init(numUsers, calendar, traceFlag);

            Log.printLine("Initialising...");

            // Create cloud storage
            CloudStorage cloud = new CloudStorage("CloudDataCenter");

            // Create fog nodes
            FogNode fog1 = new FogNode("FogNode1", cloud);
            FogNode fog2 = new FogNode("FogNode2", cloud);

            // Create proxy server
            ProxyServer proxy = new ProxyServer("ProxyServer", new FogNode[]{fog1, fog2});

            // Create edge devices (cameras)
            CameraDevice[] cameras = {
                new CameraDevice("Camera1"),
                new CameraDevice("Camera2"),
                new CameraDevice("Camera3"),
                new CameraDevice("Camera4")
            };

            // Start simulation
            TaskOffloadingManager manager = new TaskOffloadingManager(cameras, proxy);
            manager.scheduleTasks();

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Output results
            Log.printLine("Simulation completed.");
            Log.printLine("Smart Parking Simulation Completed.");
            Log.printLine("Cloud Storage Used: " + cloud.getStorageUsed() + " MB");
            Log.printLine(fog1.getName() + " Energy Used: " + fog1.getEnergyConsumed() + " J");
            Log.printLine(fog2.getName() + " Energy Used: " + fog2.getEnergyConsumed() + " J");
            Log.printLine(proxy.getName() + " Energy Used: " + proxy.getEnergyConsumed() + " J");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Simulation terminated due to an error.");
        }
    }

    /** Tier 1: IoT Edge Camera */
    public static class CameraDevice {
        private final String name;

        public CameraDevice(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /** Tier 2: Fog Node */
    public static class FogNode extends SimEntity {
        private double energyConsumed = 0.0;
        private final CloudStorage cloud;

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

        public void processImage(CameraDevice device) {
            double imageSizeMB = 5;
            double resultSizeMB = 0.5;
            double energyPerMB = 0.02;

            energyConsumed += imageSizeMB * energyPerMB;

            Log.printLine(getName() + " processed image from " + device.getName());

            // Upload result to cloud
            cloud.storeData(resultSizeMB);
            Log.printLine(getName() + " uploaded result of " + device.getName() + " to Cloud (" + resultSizeMB + " MB)");
        }

        public double getEnergyConsumed() {
            return energyConsumed;
        }
    }

    /** Tier 3: Cloud Storage */
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

        public void storeData(double dataMB) {
            storageUsed += dataMB;
        }

        public double getStorageUsed() {
            return storageUsed;
        }
    }

    /** Proxy Server between Edge and Fog */
    public static class ProxyServer extends SimEntity {
        private final FogNode[] fogNodes;
        private double energyConsumed = 0.0;

        public ProxyServer(String name, FogNode[] fogNodes) {
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
            if (ev.getTag() == 1000 && ev.getData() instanceof CameraDevice) {
                CameraDevice cam = (CameraDevice) ev.getData();
                offloadToFog(cam);
            }
        }

        private void offloadToFog(CameraDevice cam) {
            FogNode selectedFog = fogNodes[new Random().nextInt(fogNodes.length)];
            energyConsumed += 0.01;

            Log.printLine(getName() + " forwarding " + cam.getName() + " to " + selectedFog.getName());
            selectedFog.processImage(cam);
        }

        public double getEnergyConsumed() {
            return energyConsumed;
        }
    }

    /** Task Offloading Logic */
    public static class TaskOffloadingManager {
        private final CameraDevice[] cameras;
        private final ProxyServer proxy;

        public TaskOffloadingManager(CameraDevice[] cameras, ProxyServer proxy) {
            this.cameras = cameras;
            this.proxy = proxy;
        }

        public void scheduleTasks() {
            for (CameraDevice cam : cameras) {
                Log.printLine(cam.getName() + " sending to ProxyServer...");
                CloudSim.send(proxy.getId(), proxy.getId(), 0.0, 1000, cam);
            }
        }
    }
}
