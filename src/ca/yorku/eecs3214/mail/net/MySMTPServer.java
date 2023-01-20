package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailWriter;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;
import ca.yorku.eecs3214.mail.mailbox.Mailbox.InvalidUserException;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MySMTPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;
    private String domainName;
    private boolean EHLOreceieved = false;
    private boolean MAILreceived = false;
    private List<Mailbox> recipients = new ArrayList<Mailbox>();
    private MailWriter mailWriter;

    // TODO Additional properties, if needed

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's information.
     */
    public MySMTPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the initial welcome message, and then repeatedly
     * read requests, process the individual operation, and return a response, according to the SMTP protocol. Empty
     * request lines should be ignored. Only returns if the connection is terminated or if the QUIT command is issued.
     * Must close the socket connection before returning.
     */
    @Override
    public void run() {
        try (this.socket) {

        	//send initial welcome message
        	socketOut.write("220 " + getHostName() +  " Simple Mail Transfer Service Ready\n");
        	socketOut.flush();
        	
        	//read and process requests according to SMTP protocol (only returns if the connection is terminated or if the QUIT command is issued 	
        	while(socket.isConnected()) {
        		String[] request = socketIn.readLine().split(" ");
        		
        		if(request[0].toUpperCase().equals("QUIT")) { //handle QUIT
                	socketOut.write("221 MySMTPServer Service closing transmission channel\n");
                	socketOut.flush();
                	socket.close();
                	return;
        		}
        		
        		if(request[0].toUpperCase().equals("NOOP")) { //handle NOOP
                	socketOut.write("250 OK\n");
                	socketOut.flush();
        		}
        		
        		if(request[0].toUpperCase().equals("EHLO") || request[0].toUpperCase().equals("HELO")) { //handle HELO/EHLO
        			domainName = request[1];
        			EHLOreceieved = true;
                	socketOut.write("250 " + getHostName() + " greets " + domainName + "\n");
                	socketOut.flush();
        		}
        		
        		if(request[0].toUpperCase().equals("VRFY")) { //handle VRFY
        			if(request.length != 2) { //invalid arguments
                    	socketOut.write("501 Incorrect command format\n");
                    	socketOut.flush();
        			}else if (verifyUsername(request[1])){
                    	socketOut.write("250 " + request[1] + "\n");
                    	socketOut.flush();
        			}else {
                    	socketOut.write("550 Username does not exist\n");
                    	socketOut.flush();
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("MAIL")) { //handle MAIL FROM
        			if(request.length != 2 || !request[1].toUpperCase().matches("FROM:<.*>")) { //invalid arguments
                    	socketOut.write("501 Incorrect command format\n");
                    	socketOut.flush();
        			}else if(EHLOreceieved == false) { //if we did not receive an EHLO prior to receiving MAIL FROM request
                    	socketOut.write("503 EHLO command not received\n");
                    	socketOut.flush();
        			}else {
        				MAILreceived = true; //we received the MAIL command
        				recipients.clear(); //clear the recipients list
                    	socketOut.write("250 OK\n");
                    	socketOut.flush();        				
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("RCPT")) {
        			if(request.length != 2 || !request[1].toUpperCase().matches("TO:<.*>")) { //invalid arguments
                    	socketOut.write("501 Incorrect command format\n");
                    	socketOut.flush();
        			}else if(MAILreceived == false) { //if we did not receive a MAIL request prior to the RCPT request
                    	socketOut.write("503 MAIL command not received\n");
                    	socketOut.flush();
        			}else {
        				try {
	        				String recipient = request[1].substring(4, request[1].length() - 1);
		        			recipients.add(new Mailbox(recipient)); //add the new recipient's mailbox to the recipients list    				
	        				socketOut.write("250 OK\n");
	                    	socketOut.flush();

        				}catch(InvalidUserException e) { //if the destination address could not be found
            				socketOut.write("550 mailbox not found\n");
                        	socketOut.flush();
        				}
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("DATA")) {
        			if(recipients.isEmpty()) { //no recipients
                    	socketOut.write("503 bad sequence of commands\n");
                    	socketOut.flush();
        			}else {
        				socketOut.write("354 enter the message body, followed by a <CRLF>.<CRLF>\n");
                    	socketOut.flush();
                		mailWriter = new MailWriter(recipients);
                		String line = socketIn.readLine();
                		while(!line.equals(".")) {
                    		mailWriter.write(line + "\n");
                    		line = socketIn.readLine(); //read next line
                    	}
                    	mailWriter.flush();
                    	mailWriter.close();
                    	socketOut.write("250 OK\n");
                    	socketOut.flush();
        			}
        		}
        		
        		if(request[0].toUpperCase().equals("RSET")) {
        			recipients.clear();
        			MAILreceived = false;
                    socketOut.write("250 OK\n");
                    socketOut.flush();
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
     * Retrieves the name of the current host. Used in the response of commands like HELO and EHLO.
     * @return A string corresponding to the name of the current host.
     */
    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try (BufferedReader reader = Runtime.getRuntime().exec(new String[] {"hostname"}).inputReader()) {
                return reader.readLine();
            } catch (IOException ex) {
                return "unknown_host";
            }
        }
    }

    /**
     * Main process for the SMTP server. Handles the argument parsing and creates a listening server socket. Repeatedly
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
                    MySMTPServer handler = new MySMTPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}