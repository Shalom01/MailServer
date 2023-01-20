package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailMessage;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;
import ca.yorku.eecs3214.mail.mailbox.Mailbox.MailboxNotAuthenticatedException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MyPOPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;
    private String user;
    private boolean authorizationState;
    private boolean transactionState;
    private Mailbox mailbox;

    // TODO Additional properties, if needed

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's information.
     */
    public MyPOPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the initial welcome message, and then repeatedly
     * read requests, process the individual operation, and return a response, according to the POP3 protocol. Empty
     * request lines should be ignored. Only returns if the connection is terminated or if the QUIT command is issued.
     * Must close the socket connection before returning.
     */
    @Override
    public void run() {
        try (this.socket) {
        	
        	socketOut.write("+OK POP3 server ready\n");
        	socketOut.flush();
        	
        	authorizationState = true;
        	transactionState = false;

        	while(socket.isConnected()) {
        		
        		String[] request = socketIn.readLine().split(" ");
        		
        		if(request[0].toUpperCase().equals("QUIT")) { //handle QUIT
					
        			if(mailbox != null) {
						mailbox.deleteMessagesTaggedForDeletion();
					}
					
        			socketOut.write("+OK POP3 server signing off\n");
                	socketOut.flush();
                	socket.close();
                	return;
        		}
        		
        		if(request[0].toUpperCase().equals("NOOP")) { //handle NOOP
        			socketOut.write("+OK hello\n");
                	socketOut.flush();
        		}
        		
        		if(request[0].toUpperCase().equals("USER")) { //handle USER
        			if(!authorizationState) {
    					socketOut.write("-ERR already logged in\n");
    					socketOut.flush();				
        			}else {
	        			if(request.length != 2 || !verifyUsername(request[1])){
	            			socketOut.write("-ERR " + user + " is not a valid mailbox\n");
	                    	socketOut.flush();
	        			}else {
	        				user = request[1];
	            			socketOut.write("+OK " + user + " is a valid mailbox\n");
	                    	socketOut.flush();
	        			}
        			}
        		}

        		if(request[0].toUpperCase().equals("PASS")) { //handle PASS
        			if(!authorizationState) {
    					socketOut.write("-ERR already logged in\n");
    					socketOut.flush();
        			}else {
        				if(request.length != 2){
                			socketOut.write("-ERR PASS must have a single argument\n");
                        	socketOut.flush();
        				}else if(user != null){
            				try {
            					mailbox = new Mailbox(user);
            					mailbox.loadMessages(request[1]);
            					transactionState = true;
                    			authorizationState = false;
            					socketOut.write("+OK " + user + " has " + mailbox.size(false) + " messages\n");
            					socketOut.flush();				
            				}catch(MailboxNotAuthenticatedException e) {
            					socketOut.write("-ERR invalid password\n");
            					socketOut.flush();
            				}
        				}
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("STAT")) { //handle STAT
        			if(!transactionState) {
    					socketOut.write("-ERR please log in\n");
    					socketOut.flush();				
        			}else {
    					socketOut.write("+OK " + mailbox.size(false) + " " + mailbox.getTotalUndeletedFileSize(false) + "\n");
    					socketOut.flush();				
        			}	
        		}
        		
        		if(request[0].toUpperCase().equals("LIST")) { //handle LIST
        			if(!transactionState) {
    					socketOut.write("-ERR please log in\n");
    					socketOut.flush();				
        			}else {
        				if(request.length == 1){ //no argument LIST
        					socketOut.write("+OK " + mailbox.size(false) + " messages " + "(" + mailbox.getTotalUndeletedFileSize(false) + " bytes)\n");
        					for(int i = 1; i <= mailbox.size(true); i++) {
        						if(!mailbox.getMailMessage(i).isDeleted()) {
        							socketOut.write(i + " " + mailbox.getMailMessage(i).getFileSize() + "\n");
        						}
        					}
        					socketOut.write(".\n");
        					socketOut.flush();
	        			}else if(request.length == 2) { //argument LIST
	        				try{
	        					if(mailbox.getMailMessage(Integer.parseInt(request[1])).isDeleted()){
	    	    					socketOut.write("-ERR message was deleted\n");
	    	    					socketOut.flush();		
	        					}else {
	        						socketOut.write("+OK " + request[1] + " " + mailbox.getMailMessage(Integer.parseInt(request[1])).getFileSize() + "\n");
	        						socketOut.flush();
	        					}
	        				}catch(IndexOutOfBoundsException e) {
        						socketOut.write("-ERR index is out of bounds\n");
        						socketOut.flush();
	        				}
	        			}
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("DELE")) { //handle DELE
        			if(!transactionState) {
    					socketOut.write("-ERR please log in\n");
    					socketOut.flush();				
        			}else {
	        			if(request.length != 2){
	    					socketOut.write("-ERR please input file number\n");
	    					socketOut.flush();
	        			}else {
	        				try {
	        					MailMessage message = mailbox.getMailMessage(Integer.parseInt(request[1]));
	        					message.tagForDeletion();
	        					socketOut.write("+OK message deleted\n");
	        					socketOut.flush();
	        				}catch(IndexOutOfBoundsException e) {
	        					socketOut.write("-ERR index is out of bounds\n");
	        					socketOut.flush();	
	        				}
	        			}
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("RSET")) { //handle RSET
        			if(!transactionState) {
    					socketOut.write("-ERR please log in\n");
    					socketOut.flush();				
        			}else {
        				for(int i = 1; i <= mailbox.size(true); i++) {
        					MailMessage message = mailbox.getMailMessage(i);
        					if(message.isDeleted()) {
        						message.undelete();
        					}
        				}
    					socketOut.write("+OK mailbox has " + mailbox.size(false) + " messages " + "(" + mailbox.size(false) + " bytes)\n");
    					socketOut.flush();
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("RETR")) { //handle RSET
        			if(!transactionState) {
    					socketOut.write("-ERR please log in\n");
    					socketOut.flush();				
        			}else {
        				try {
		        			if(mailbox.getMailMessage(Integer.parseInt(request[1])).isDeleted()){
		    					socketOut.write("-ERR message was deleted\n");
		    					socketOut.flush();		
		        			}else {
		    	    				socketOut.write("+OK " + mailbox.getMailMessage(Integer.parseInt(request[1])).getFileSize() + " bytes\n");
		    	    				socketOut.flush();
		    	    				
		    	    				BufferedReader reader = new BufferedReader(new FileReader(mailbox.getMailMessage(Integer.parseInt(request[1])).getFile()));
		        					String line = reader.readLine();
		    	    				while(line != null) {
		    	    					System.out.println(line + "\n");
		    	    					socketOut.write(line + "\n");
		    	    					line = reader.readLine();
		        					}
	    	    					socketOut.write(".\n");
		    	    				socketOut.flush();
		        			}
        				}catch(IndexOutOfBoundsException e) {
	    					socketOut.write("-ERR index is out of bounds\n");
	    					socketOut.flush();		
        				}
        			}	
        		}
        	}
        } catch (IOException e) {
            System.err.println("Error in client's connection handling.");
            e.printStackTrace();
        }
    }
    
    /**
     * Helper method for run() that checks if the passed user string is in the users.txt file
     * @param String user, the username and domain formatted as (username@domain format) 
     * @returns true if in file, else false
     */
    private boolean verifyUsername(String user) {
		try {
	    	File users = new File("users.txt");
			BufferedReader reader = new BufferedReader(new FileReader(users));
			String line;
			while((line = reader.readLine()) != null) {
				String[] username = line.split(" ");
				if(user.equalsIgnoreCase(username[0])) {
					reader.close();
					return true;
				}
			}	
			reader.close();
		}catch(IOException e){
			e.printStackTrace();
		}
    	return false;
    }    

    /**
     * Main process for the POP3 server. Handles the argument parsing and creates a listening server socket. Repeatedly
     * accepts new connections from individual clients, creating a new server instance that handles communication with
     * that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException("This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);

            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MyPOPServer handler = new MyPOPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}