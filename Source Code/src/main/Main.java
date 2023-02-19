package main;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Main {
	
	public static void main(String[] args) throws InterruptedException, UnknownHostException, IOException {
		
		Platoon platoon = new Platoon("127.0.0.1", 4444);
		Truck truck1 = new Truck(1);
		Truck truck2 = new Truck(2);
		Truck truck3 = new Truck(3);
		Truck truck4 = new Truck(4);
		Truck truck5 = new Truck(5);
		Truck truck6 = new Truck(6);
		
		Thread truck1Thread = new Thread(truck1);
		Thread truck2Thread = new Thread(truck2);
		Thread truck3Thread = new Thread(truck3);
		Thread truck4Thread = new Thread(truck4);
		Thread truck5Thread = new Thread(truck5);
		Thread truck6Thread = new Thread(truck6);
		
		truck1Thread.start();
		truck2Thread.start();
		truck3Thread.start();
		truck4Thread.start();
		truck5Thread.start();
		truck6Thread.start();
			
		Timer timer1 = new Timer();
	    timer1.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	try {
	        		ArrayList<Truck> matchedTrucks = new ArrayList<Truck>();
	        		matchedTrucks.add(truck1);
	        		matchedTrucks.add(truck2);
	        		platoon.matchTrucks(matchedTrucks);
	        		platoon.formPlatoon(matchedTrucks);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
	        }
	    },2000);
	    
	    Timer timer2 = new Timer();
	    timer2.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck3.joinPlatoon(platoon);
	        }
	    },4000);
	    	
	    Timer timer3 = new Timer();
	    timer3.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck4.joinPlatoon(platoon);
	        }
	    },5000);
	    
	    Timer timer4 = new Timer();
	    timer4.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck1.accelerate(0.3, 1);
	        }
	    },9000);
	        
	    Timer timer5 = new Timer();
	    timer5.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck1.accelerate(-0.3, 1);
	        }
	    },12000);
	    
	    Timer timer6 = new Timer();
	    timer6.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck2.leavePlatoon();
	        }
	    },24000);
	    
	  
	    Timer timer7 = new Timer();
	    timer7.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck5.joinPlatoon(platoon);
	        }
	    },30000);
	    
	    
	    Timer timer8 = new Timer();
	    timer8.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck6.joinPlatoon(platoon);
	        }
	    },36000);
	    
	    Timer timer9 = new Timer();
	    timer9.schedule(new TimerTask() {
	        @Override
	        public void run() {
	        	truck5.emergencyBreak();
	        }
	    },40000);
	}

}
