package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.sql.Struct;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import org.json.*;

import java.util.Random;

enum TruckState{
    STANDALONE,
    DRIVING,
    ENGAGING,
    LEAVING,
    EMERGENCY_BREAKING
}

public class Truck implements Runnable{
	private int id;
	private boolean is_leader;
	private double speed = 15;
	private double max_speed = 25;
	private double acceleration = 0;
	private int wheel_angle = 0;
    private boolean finished = false;
    private Enum<TruckState> state; 
	private int max_deceleration = -1;
	private int max_acceleration = 1;

	private double gap_from_platoon;
	
	private Platoon platoon;
	private JSONObject platoonData;
    
    private InputHandler inputHandler;
    private Timer sendDataTimer;
    private Timer gapAdaptationTimer;
    private Timer accelerationTimer;

    private Socket clientSocket;
    private BufferedWriter out;
    private BufferedReader in;
    

    public Truck(int id) {
		this.id = id;
		setState(TruckState.STANDALONE);
		this.setLeader(false);
		platoonData = new JSONObject();
    }
	  
	@Override
	public void run() {
		this.drive();
		System.out.println("Truck " + getTruckId() + " running on " + Thread.currentThread().getName());
	}
	  
  
	public void drive() {
		setState(TruckState.DRIVING);
	}
	  
	public int getTruckId() {
		return this.id;
	}

	public boolean isLeader() {
		return is_leader;
	}
	
	public void setLeader(boolean is_leader) {
		this.is_leader = is_leader;
	}
	
	public double getAcceleration() {
		return this.acceleration;
	}
	
	public void setWheelAngle(int wheel_angle) {
		this.wheel_angle = wheel_angle;
	}
	
	
	public double getWheelAngle() {
		return this.wheel_angle;
	}
	
	public double getSpeed() {
		return speed;
	}

  	public void accelerate(double acceleration, double accelerationSeconds){
  		accelerationTimer = new Timer();    
  		accelerationTimer.scheduleAtFixedRate(accelerationTask(acceleration, accelerationSeconds), 0, 1000);
	}
  	
  	private TimerTask accelerationTask(double newAcceleration, double accelerationSeconds) {
		return new TimerTask() {
			double counter = 0;
			@Override
			public void run() {
				try {
					acceleration = newAcceleration;
					
					if(speed+acceleration < max_speed && speed+acceleration > 0) {
						speed += acceleration;
					}else if(speed+acceleration > max_speed){
						speed = max_speed;
						this.cancel();
					}else if(speed+acceleration < 0){
						speed = 0;
						acceleration = 0;
						this.cancel();
					}
					
					
					if(accelerationSeconds != 0) {
						System.out.println("Truck " + getTruckId() + " set acceleration to " + acceleration + " for " + accelerationSeconds + " seconds.");
						if(counter >= accelerationSeconds) {
							acceleration = 0;
							this.cancel();
						}
					}else {
						System.out.println("Truck " + getTruckId() + " set acceleration to " + acceleration);
						System.out.println("Truck " + getTruckId() + " speed is " + speed);
					}
					
					counter++;
					
				} catch (Exception e) {
					this.cancel();
				}
			}
	    };
	}

	
	public JSONObject getCoordinates() {
		JSONObject data = new JSONObject();
         
		data.put("lat", Math.random() * (90 - (-90)) + (-90));
		data.put("long", Math.random() * (180 - (-180)) + (-180));
		return data;
	}
	
	
  	void emergencyBreak(){
  		new Thread(()->{
  			setState(TruckState.EMERGENCY_BREAKING);
			try {
				JSONObject data = new JSONObject();
				data.put("type", RequestTypes.REQUEST_EMERGENCY_BREAKE);
				data.put("truck_id", getTruckId());
				data.put("acceleration", max_deceleration);
		        sendMessage(data.toString());	 
			} catch (IOException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}).start();
	}
	
	
	public void startConnection(String endpoint, int port) throws UnknownHostException, IOException {
        clientSocket = new Socket(endpoint, port);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        System.out.println("Truck " + getTruckId() + " started communication with Server ");
        inputHandler = new InputHandler();
        Thread t = new Thread(inputHandler);
        t.start();
    }

    private TimerTask sendingDataTask() {
		return new TimerTask() {	   
			@Override
			public void run() {
				try {
					JSONObject data = new JSONObject();
					data.put("type", RequestTypes.REQUEST_SEND_PLATOONING_DATA);
					data.put("leader_id", getTruckId());
			  		data.put("speed", getSpeed());
			  		data.put("acceleration", getAcceleration());
			  		data.put("coordinates", getCoordinates());
			  		data.put("wheel_angle", getWheelAngle());
			  		sendMessage(data.toString());
				} catch (Exception e) {
					this.cancel();
				}
			}
	    };
	}
    
    
    private TimerTask gapAdaptationTask() {
		return new TimerTask() {
			int counter = 0;
			@Override
			public void run() {
				try {
											
					if(platoonData.has("acceleration")) {
						
						if(gap_from_platoon > platoon.getRangeGap()) {
							if(speed + 1*max_acceleration > max_speed) {
								speed = max_speed;
								acceleration = 0;
							}else {
								speed += 1 * max_acceleration;
								acceleration = max_acceleration;
							}
							
							gap_from_platoon -= ((speed - platoonData.getDouble("speed")) + 0.5*((Math.pow(counter, 2)-Math.pow(counter-1, 2)) * (acceleration - platoonData.getDouble("acceleration"))));
						}else if(gap_from_platoon <= platoon.getRangeGap()) {
							acceleration = platoonData.getDouble("acceleration");
									
							if(speed - Math.abs(acceleration) > platoonData.getDouble("speed") && gap_from_platoon > platoon.getGap()) {
								acceleration -= 0.3;
								speed = speed - Math.abs(acceleration);
							}else if(speed + Math.abs(acceleration) < platoonData.getDouble("speed") && gap_from_platoon > platoon.getGap()) {
								acceleration += 0.1;
								speed = speed + Math.abs(acceleration);
							}else if(gap_from_platoon < platoon.getGap()) {
								acceleration -= 0.1;
								speed = speed - Math.abs(acceleration);
							}
							
							gap_from_platoon -= ((speed - platoonData.getDouble("speed")) + 0.5*((Math.pow(counter, 2)-Math.pow(counter-1, 2)) * (acceleration - platoonData.getDouble("acceleration"))));
						}						
					}
					
//					System.out.println("Truck " + getTruckId() + " speed " + speed + " distance " + gap_from_platoon);
//					
					counter++;
				} catch (Exception e) {
					System.out.println(e.getMessage());
					this.cancel();
				}
			}
	    };
	}
   

	public void sendMessage(String msg) throws IOException {
		out.write(msg);
		out.newLine();
        out.flush();
    }

    public boolean stopConnection() throws IOException {
    	if(isLeader()) {
    		sendDataTimer.cancel();
    	}
    	gapAdaptationTimer.cancel();
    	inputHandler.stop();
    	in.close();
        out.close();
    	clientSocket.close();
        return true;
    }

    public void joinPlatoon(Platoon platoon) {
		this.setState(TruckState.ENGAGING);
		this.sendEngageRequest(platoon);
	}
    
    public void leavePlatoon() {
		this.setState(TruckState.LEAVING);
		this.sendLeavingRequest();
	}

	public void sendEngageRequest(Platoon _platoon) {
		new Thread(()->{
			try {		
				platoon = _platoon;
				clientSocket = new Socket(platoon.getServerEndPoint(),platoon.getServerPort());
		        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		        out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		        
		        inputHandler = new InputHandler();
		        Thread t = new Thread(inputHandler);
		        t.start();
		        
		        JSONObject data = new JSONObject();
				data.put("type", RequestTypes.REQUEST_ENGAGE_REQUESTED);
		  		data.put("truck_id", this.getTruckId());
		  		sendMessage(data.toString());
			} catch (Exception e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}).start();
	}
	
	public void sendLeavingRequest() {
		new Thread(()->{
			try {
				JSONObject data = new JSONObject();
				data.put("type", RequestTypes.REQUEST_LEAVE);
		  		data.put("truck_id", getTruckId());
		        sendMessage(data.toString());
		        stopConnection();
		        this.inputHandler.stop();
		        return;
			} catch (IOException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}).start();
	}
	
	
	private void decideTruckEngage(int id) {
		new Thread(()->{
			JSONObject data;
			data = new JSONObject();
			Random randomGenerator = new Random(); // randomGenerator.nextBoolean()
			
			if(true) {
				data.put("type", RequestTypes.REQUEST_ENGAGE_ACCEPTED);
			}else {
				data.put("type", RequestTypes.REQUEST_ENGAGE_REJECTED);
			}
	  		data.put("truck_id", id);

	  		try {
				sendMessage(data.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}
	  		return;
		}).start();
	}
	
	public Platoon getPlatoon() {
		return platoon;
	}
	
	public void setPlatoon(Platoon platoon) {
		this.platoon = platoon;
	}
	
	
	public void acceptFormPlatoon() {
		new Thread(()->{
			JSONObject data;
			data = new JSONObject();
			data.put("type", RequestTypes.REQUEST_FORM_PLATOON_ACCEPTED);
			data.put("truck_id", getTruckId());
			
			try {
				sendMessage(data.toString());
				
			} catch (IOException e) {
				e.printStackTrace();
			}
	  		return;
		}).start();
	}
	
	public Enum<TruckState> getState() {
		return state;
	}

	public void setState(Enum<TruckState> state) {
		this.state = state;
	}

	class InputHandler implements Runnable{

		@Override
		public void run() {
			
            JSONObject data;
            String type;
            String inputLine;
            
			while(!finished) {
				try {
					inputLine = in.readLine();
					data = new JSONObject(inputLine);
					type = (String) data.get("type");
					
					System.out.println("Truck " + getTruckId() + " reads " + inputLine + " on " + Thread.currentThread().getName());
					
					if(type.equals(RequestTypes.REQUEST_FORM_PLATOON.name())) {
						acceptFormPlatoon();
					}
					else if(type.equals(ResponseTypes.RESPONSE_FORM_PLATOON_ACCEPTED.name())) {
					    setState(TruckState.DRIVING);
						if(data.has("is_leader") && data.get("is_leader").equals(1)) {							
							setLeader(true);
							System.out.println("Truck " + getTruckId() + " is Leader");
							sendDataTimer = new Timer();    
					    	sendDataTimer.scheduleAtFixedRate(sendingDataTask(), 0, 2000);
						}else {
							gap_from_platoon = 110;
							gapAdaptationTimer = new Timer();    
							gapAdaptationTimer.scheduleAtFixedRate(gapAdaptationTask(), 0, 1000);
						}
					}
					else if(type.equals(RequestTypes.REQUEST_ENGAGE_REQUESTED.name())) {
						 if(isLeader() == true) {
							 decideTruckEngage((int) data.get("truck_id"));
						 }
					}
					else if(type.equals(ResponseTypes.RESPONSE_ENGAGE_ACCEPTED.name())) {
					    setState(TruckState.DRIVING);
					    gap_from_platoon = 200;
					    gapAdaptationTimer = new Timer();    
						gapAdaptationTimer.scheduleAtFixedRate(gapAdaptationTask(), 0, 1000);
					}
					else if(type.equals(ResponseTypes.RESPONSE_ENGAGE_REJECTED.name())) {
						stopConnection();
					}
					else if(type.equals(ResponseTypes.RESPONSE_EMERGENCY_BREAKE.name())) {
			        	if(!isLeader()) {
			        		System.out.println("Truck " + getTruckId() + " emergency breaking");
			        		accelerate(data.getDouble("acceleration"), 0);
				        	stopConnection();
			        	}
					}
					else if(type.equals(RequestTypes.REQUEST_SEND_PLATOONING_DATA.name())){
						setState(TruckState.DRIVING);
						if(!isLeader()) {
							platoonData = data;
						}
					}
				} catch (Exception e) {
					
					Thread.currentThread().interrupt();
				}
			}
			Thread.currentThread().interrupt();
		}
		
		public void stop() {
	        finished = true;
	    }
	}
}
