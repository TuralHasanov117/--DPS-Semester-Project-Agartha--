package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;


enum RequestTypes{
	REQUEST_FORM_PLATOON,
	REQUEST_FORM_PLATOON_ACCEPTED,
	REQUEST_ENGAGE_REQUESTED,
	REQUEST_ENGAGE_ACCEPTED,
	REQUEST_ENGAGE_REJECTED,
	REQUEST_LEAVE,
	REQUEST_EMERGENCY_BREAKE,
	REQUEST_SEND_PLATOONING_DATA
}

enum ResponseTypes{
	RESPONSE_FORM_PLATOON_ACCEPTED,
	RESPONSE_ENGAGE_ACCEPTED,
	RESPONSE_ENGAGE_REJECTED,
	RESPONSE_LEAVE,
	RESPONSE_EMERGENCY_BREAKE, 
}


public class PlatoonServer implements Runnable{
	
	private ServerSocket serverSocket;
    private boolean is_running;
    public ArrayList<EchoClientHandler> clients;
    private ExecutorService pool;
    private int serverPort;
    private int matchedClientsSize = 0;
    private String serverEndPoint;
    private ArrayList<ClientPending> clientsPending;
    private ArrayList<EchoClientHandler> matchedClients;
    private boolean isPlatoonRunning = false;

    
    public PlatoonServer(String endpoint, int port) {
    	this.setServerEndPoint(endpoint);
    	this.setServerPort(port);
    }
    
    @Override
    public void run(){
        try {
			serverSocket = new ServerSocket(serverPort);
		} catch (IOException e) {
			e.printStackTrace();
		}
        pool = Executors.newCachedThreadPool();
        clients = new ArrayList<EchoClientHandler>();
        clientsPending = new ArrayList<ClientPending>();
        matchedClients = new ArrayList<EchoClientHandler>();
        is_running = true;
        
        System.out.println("Platoon Server running on " + Thread.currentThread().getName());
        
        while (true)
        {
        	EchoClientHandler handler;
			try {
				handler = new EchoClientHandler(serverSocket.accept());
	        	pool.execute(handler);
	        	if(matchedClientsSize > matchedClients.size()) {
					matchedClients.add(handler);
				}
			} catch (IOException e) {
				Thread.currentThread().interrupt();
			}      	
        }
    }
    
    public void setMatchedClientsSize(int size) {
    	this.matchedClientsSize = size;
    }

    public void shutdown() throws IOException {
    	pool.shutdown();
        if(!serverSocket.isClosed()) {
        	serverSocket.close();
        }
    }
    
    public boolean isRunning() {
    	return this.is_running;
    }
    
    public EchoClientHandler leaderClient() {
		return clients.stream().filter(c -> c.isLeader == true).findAny().get();
    }
    

    public String getServerEndPoint() {
		return this.serverEndPoint;
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
	
	private class ClientPending{
		private int id;
		private EchoClientHandler client;
		
		public ClientPending(int id, EchoClientHandler client) {
			this.setId(id);
			this.setClient(client);
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public EchoClientHandler getClient() {
			return client;
		}

		public void setClient(EchoClientHandler client) {
			this.client = client;
		}
	}
	
	public void sendFormPlatoonRequest(EchoClientHandler client) {
		try {
			JSONObject request = new JSONObject();
			request.put("type", RequestTypes.REQUEST_FORM_PLATOON);
			client.sendMessage(request.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private class EchoClientHandler extends Thread {
        private Socket clientSocket;
        private BufferedReader inReader;
        private BufferedWriter outWriter;
        public boolean isLeader = false;

        public EchoClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
    	@Override
    	public void run() {
    		try {
				inReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				outWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
			} catch (IOException e) {
				Thread.currentThread().interrupt();
			}
    		if(matchedClients.contains(this)) {
    			sendFormPlatoonRequest(this);
    		}

    		String inputLine;
            JSONObject data;
            String type;
            JSONObject response;
            
            try {
				while ((inputLine = inReader.readLine()) != null) {
					
					data = new JSONObject(inputLine);
					type = (String) data.get("type");

					if(type.equals(RequestTypes.REQUEST_FORM_PLATOON_ACCEPTED.name())) {				
						clients.add(this);
						response = new JSONObject();
						response.put("type", ResponseTypes.RESPONSE_FORM_PLATOON_ACCEPTED);
						if(matchedClients.indexOf(this) == 0) {
							this.isLeader = true;
							response.put("is_leader", 1);
						}
						this.sendMessage(response.toString());
					}
					else if(type.equals(RequestTypes.REQUEST_ENGAGE_REQUESTED.name())) {
					     // send this information to leader truck to make decision
						 ClientPending newClient = new ClientPending(data.getInt("truck_id"), this);
						 clientsPending.add(newClient);
						 leaderClient().sendMessage(inputLine);	
					}
					else if(type.equals(RequestTypes.REQUEST_ENGAGE_ACCEPTED.name())) {
						final int truckId = data.getInt("truck_id");
						ClientPending client = clientsPending.stream().filter(c -> c.getId() == truckId).findAny().get();
						clients.add(client.getClient());
						response = new JSONObject();
						response.put("type", ResponseTypes.RESPONSE_ENGAGE_ACCEPTED);
						client.getClient().sendMessage(response.toString());
					}
					else if(type.equals(RequestTypes.REQUEST_ENGAGE_REJECTED.name())) {
					     // send reject response to engaging truck
						response = new JSONObject();
						response.put("type", ResponseTypes.RESPONSE_ENGAGE_REJECTED);
						final int truckId = data.getInt("truck_id");
						ClientPending client = clientsPending.stream().filter(c -> c.getId() == truckId).findAny().get();

						client.getClient().sendMessage(response.toString());
						clientsPending.remove(client);
					}
					else if(type.equals(RequestTypes.REQUEST_LEAVE.name())){
						response = new JSONObject();
						response.put("type", ResponseTypes.RESPONSE_LEAVE);
						response.put("truck_id", data.get("truck_id"));
						broadcast(response.toString());
						clients.remove(this);
					}
					else if(type.equals(RequestTypes.REQUEST_EMERGENCY_BREAKE.name())){		
						List<EchoClientHandler> clientsToBroadcast = clients.subList(clients.indexOf(this), clients.size());
						response = new JSONObject();
						response.put("type", ResponseTypes.RESPONSE_EMERGENCY_BREAKE);
						response.put("truck_id", data.get("truck_id"));
						response.put("acceleration", data.get("acceleration"));
						leaderClient().sendMessage(response.toString());	
						broadcast(response.toString(), clientsToBroadcast);
					}
					else if(type.equals(RequestTypes.REQUEST_SEND_PLATOONING_DATA.name())){
						broadcast(inputLine);
					}
				}
			} catch (IOException e) {
				Thread.currentThread().interrupt();
			}
    	}

        
        public void sendMessage(String message) throws IOException {
        	outWriter.write(message);
        	outWriter.newLine();
        	outWriter.flush();
        }
        
        
        public void shutdown(){
        	try {
				inReader.close();
				outWriter.close();
	        	if(!clientSocket.isClosed()) {
	        		clientSocket.close();
	        	}
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        public void broadcast(String message) throws IOException {
	    	 for (EchoClientHandler echoClient : clients) {
	 				echoClient.sendMessage(message);
			 }
        }
        
        public void broadcast(String message, List<EchoClientHandler> clientsToBroadcast) throws IOException {   
	     	  for (EchoClientHandler echoClient : clientsToBroadcast) {
	 				echoClient.sendMessage(message);
	     	  }
        }
    }
}
