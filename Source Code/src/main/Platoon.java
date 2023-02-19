package main;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

import main.Truck.InputHandler;


enum PlatoonState
{
    STANDALONE,
    FORMATION,
    ENAGING,
    PLATOONING,
    DISENGAGING,
};

class V2VRange{
	public float radius;
	
	public V2VRange(int radius) {
		this.radius = radius;
	}
};

public class Platoon{
    private Enum<PlatoonState> state;
    private PlatoonServer server;
    private int serverPort;
    private String serverEndPoint;
	private Thread serverThread;
	private V2VRange v2vRange;
	private double gap = 10;
	private double rangeGap = 100;
 

    public Platoon(String endpoint, int port) {
    	serverEndPoint = endpoint;
    	serverPort = port;
    	setState(PlatoonState.STANDALONE);
    	setV2vRange(new V2VRange(100));
    	startServer();
	}
    
    public void matchTrucks(ArrayList<Truck> trucks){
		if(trucks.size() > 0) {
			this.setLeaderTruck(trucks.get(0));
		}
		
		this.server.setMatchedClientsSize(trucks.size());
	}
    
    public void formPlatoon(ArrayList<Truck> trucks) throws UnknownHostException, IOException {
    	setState(PlatoonState.FORMATION);
    	for (Truck truck : trucks) {
			truck.setPlatoon(this);
			truck.startConnection(serverEndPoint, serverPort);
		}
	}
    
    public void setLeaderTruck(Truck truck) {
    	truck.setLeader(true);
    }
    
    
	public V2VRange getV2vRange() {
		return v2vRange;
	}

	public void setV2vRange(V2VRange v2vRange) {
		this.v2vRange = v2vRange;
	}
    
    public String getServerEndPoint() {
		return serverEndPoint;
	}

	public void setServerEndPoint(String serverEndPoint) {
		this.serverEndPoint = serverEndPoint;
	}
	
	public void setServerPort(int port) {
		this.serverPort = port;
	}
	
	public int getServerPort() {
		return this.serverPort;
	}
    
    public double getGap() {
    	return gap;
    }
	
    public void startServer() {
		server = new PlatoonServer(serverEndPoint, serverPort);
		this.serverThread = new Thread(server);
		serverThread.start();
    }

	public Enum<PlatoonState> getState() {
		return state;
	}

	public void setState(Enum<PlatoonState> state) {
		this.state = state;
	}

	public double getRangeGap() {
		return rangeGap;
	}

	public void setRangeGap(float rangeGap) {
		this.rangeGap = rangeGap;
	}
}
