import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.validation.Validator;
import java.time.DateTimeException;

class ClientHandler extends Thread {
    DatagramSocket udpServerSocket;

    public ClientHandler(int udpServerPort) throws Exception {
        this.udpServerSocket = new DatagramSocket(udpServerPort);
    }

    public void run() {

        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        ReentrantLock syncLock = new ReentrantLock();
        try {
            while (true) {
                this.udpServerSocket.receive(receivePacket);

                // when there is a connection from other client, send the fake user input "wait" to the stdin
                byte[] fakeInput = "wait".getBytes();
                ByteArrayInputStream fakeIn = new ByteArrayInputStream(fakeInput);
                System.setIn(fakeIn);

                // get the lock to make sure the transmission will not be disturbed
                syncLock.lock();
                // get the desired file name and the sender name
                // because the page is 1024 byte, the filename may not be that long, the data will be auto filled by 'null'
                // need to get the real length of the filename
                byte[] data = receivePacket.getData();
                int length = 0;
                for (byte b: data) {
                    if (b != 0) {
                        length++;
                    }
                }
                String filename = new String(data, 0, length);

                // get the desired senderName
                // same technique apply here to get the real length of the senderName
                receiveData = new byte[1024];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                this.udpServerSocket.receive(receivePacket);
                data = receivePacket.getData();
                length = 0;
                for (byte b: data) {
                    if (b != 0) {
                        length++;
                    }
                }
                String senderName = new String(data, 0, length);

                String newFileName = senderName + "_" + filename.trim();

                // get the absolute path of the new file
                String userDir = System.getProperty("user.dir").trim();
                String abPath = userDir + File.separatorChar + newFileName;

                // create the file if it doesnot exist
                File outputFile = new File(abPath);
                if (!outputFile.exists()) {
                    try {
                        outputFile.createNewFile();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // set a timeout value because there will be packet loss during the transmission
                // stop the transmission when reach timeout value
                FileOutputStream fout = new FileOutputStream(outputFile);
                this.udpServerSocket.setSoTimeout(10000);
                // get the file content
                String progressCheck;
                while (true) {
                    receiveData = new byte[1024];
                    receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    try {
                        // get the desired data
                        // same technique apply here to get the real length of the data
                        this.udpServerSocket.receive(receivePacket);
                        data = receivePacket.getData();
                        length = 0;
                        for (byte b: data) {
                            if (b != 0) {
                                length++;
                            }
                        }
                        // if the data is string 'done' -> break the while loop, transmission done
                        progressCheck = new String(data, 0, length);
                        if (progressCheck.compareTo("done") == 0) {
                            break;
                        } else {
                            fout.write(receivePacket.getData(), 0, receivePacket.getLength());
                        }
                        System.setIn(fakeIn);
                    // time out, break the while loop as well
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }

                // unlock the lock and send the fake input 'done' to stdin
                // print the confirmation message
                syncLock.unlock();
                System.out.println("Received " + filename + " from " + senderName);
                fout.close();
                fakeInput = "done".getBytes();
                fakeIn = new ByteArrayInputStream(fakeInput);
                System.setIn(fakeIn);
            }
        } catch (Exception e) {
        }

    }

    // close the client receive socket when the client hit "OUT"
    public void close() {
        this.udpServerSocket.close();
    }

}



public class Client {
    public static void main(String[] args) throws Exception{
        if(args.length != 3){
            System.out.println("Usage: java Clinet server_IP server_port client_udp_server_port");
            System.exit(1);
        }
        InetAddress IPAddress = InetAddress.getByName(args[0]);
        int TcpClientPort = Integer.parseInt(args[1]);
        int UdpServerPort = Integer.parseInt(args[2]);

        // before establishing the 3 way handshake with Server, create another UDP Server Thread for receiving the data from other client
        ClientHandler c = new ClientHandler(UdpServerPort);
        c.start();

        // three way handshake with Server
        Socket clientSocket = new Socket(IPAddress, TcpClientPort);
        
        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
        BufferedReader bfServe = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        // process the user authentication
        // if fail -> system.exit(1)
        if (clientAuthentication(bfServe, outToServer) == false) {
            System.exit(1);
        }

        // pass the user authentication -> send the 'udpportnumber' to server -> server can write to 'userlog.txt' file
        outToServer.writeBytes(Integer.toString(UdpServerPort)+"\n");

        // the client display a message to the user information of all the available command
        BufferedReader clientInput = new BufferedReader(new InputStreamReader(System.in));
        String testMessage = "Enter one of the following commands(MSG, DLT, EDT, RDM, ATU, OUT, UPD): ";
        String errorMessage = "Error. Invalid command!";
        ReentrantLock syncLock = new ReentrantLock();
        boolean condition = true;
        while (condition) {
            System.out.println(testMessage);
            String clientQuery = clientInput.readLine();

            // check if the other client is sending the data to us by exmaining the stdin
            // the stdin is 'wait' -> the other client thread is currently sending the data to us
            // the stdin is 'done' -> the other client thread finished sending data to us
            // need to acquire a lock to seperate the input from keyboard and fake input from 'client receiving thread'
            syncLock.lock();
            if (clientQuery.compareTo("wait") == 0) {
                while (true) {
                    clientQuery = clientInput.readLine();
                    if (clientQuery.compareTo("done") == 0) {
                        break;
                    }
                }
                syncLock.unlock();
                System.out.println(testMessage);
                clientQuery = clientInput.readLine();
            } else {
                syncLock.unlock();
            }

            String[] queryArg = clientQuery.split(" ");
            
            // different client handle functions for different commands
            // querArg[0] stored the command("MSG", "DLT", "EDT", "RDM", "ATU", "OUT", "UPD")
            if (queryArg[0].compareTo("MSG") == 0) {
                // check other 'MSG' command argument is valid 
                if (!checkMsg(queryArg)) {
                    System.out.println(errorMessage);
                }
                // send 'MSG' and message to server
                outToServer.writeBytes(queryArg[0] + "\n");
                outToServer.writeBytes(clientQuery.substring(4) + "\n");

                // receive the message back from server
                String userNum = bfServe.readLine();
                String currentTime = bfServe.readLine();
                String reply = "Message #" + userNum + " posted at " + currentTime + "."; 
                System.out.println(reply);
            
            } else if (queryArg[0].compareTo("DLT") == 0) {
                // check other 'DLT' command argument is valid 
                if (!checkDlt(queryArg)) {
                    System.out.println(errorMessage);
                } else {
                    // send the 'DLT', messageNum and timestamp to server to delete
                    outToServer.writeBytes(queryArg[0] + "\n");
                    outToServer.writeBytes(queryArg[1].substring(1) + "\n");
                    int startLength = (queryArg[0] + " " + queryArg[1] + " ").length();
                    outToServer.writeBytes(clientQuery.substring(startLength) + "\n");
                    
                    // recevier the message back from server
                    String reply = bfServe.readLine();
                    System.out.println(reply);
                }

            } else if (queryArg[0].compareTo("EDT") == 0) { 
                // check other 'EDT' command argument is valid 
                if (!checkEdt(queryArg)) {
                    System.out.println(errorMessage);
                } else {
                    // send the 'EDT', message, timestamp to server to EDT
                    outToServer.writeBytes(queryArg[0] + "\n");
                    outToServer.writeBytes(queryArg[1].substring(1) + "\n");

                    int startLength = (queryArg[0] + " " + queryArg[1] + " ").length();
                    int endLength = (queryArg[0] + " " + queryArg[1] + " " + queryArg[2] + " " + queryArg[3] + " " + queryArg[4] + " " + queryArg[5]).length();
                    outToServer.writeBytes(clientQuery.substring(startLength, endLength) + "\n");
                    outToServer.writeBytes(clientQuery.substring(endLength+1) + "\n");

                    //receive the message back from server
                    String reply = bfServe.readLine();
                    System.out.println(reply);
                }

            } else if (queryArg[0].compareTo("RDM") == 0) {
                // check other 'RDM' command argument is valid 
                if (!checkRdm(queryArg)) {
                    System.out.println(errorMessage);
                } else {
                    // send the 'RDM', timestamp to server to RDM
                    outToServer.writeBytes(queryArg[0] + "\n");
                    outToServer.writeBytes(clientQuery.substring(4) + "\n");

                    // receive the message back from server
                    // receive 0 -> only one more line to read from server
                    // receive more than 0 -> read that number of time from server
                    String replyType = bfServe.readLine();
                    if (replyType.compareTo("0") == 0) {
                        String reply = bfServe.readLine();
                        System.out.println(reply);
                    } else {
                        for (int i = 0; i < Integer.parseInt(replyType); i++) {
                            String reply = bfServe.readLine();
                            System.out.println(reply);
                        }
                    }
                }

            } else if (queryArg[0].compareTo("ATU") == 0) {
                // check other 'ATU' command argument is valid 
                if (!checkAtu(queryArg)) {
                    System.out.println(errorMessage);
                } else {
                    // send the 'ATU'
                    outToServer.writeBytes(queryArg[0] + "\n");

                    // receive the message back from server
                    // receive 0 -> only one more line to read from server
                    // receive more than 0 -> read that number of time from server
                    String replyType = bfServe.readLine();
                    if (replyType.compareTo("0") == 0) {
                        String reply = bfServe.readLine();
                        System.out.println(reply);
                    } else {
                        for (int i = 0; i < Integer.parseInt(replyType); i++) {
                            String reply = bfServe.readLine();
                            System.out.println(reply);
                        }
                    }
                }

            } else if (queryArg[0].compareTo("OUT") == 0) {
                // check other 'OUT' command argument is valid 
                if (!checkOut(queryArg)) {
                    System.out.println(errorMessage);
                } else {
                    // send the 'ATU'
                    outToServer.writeBytes(queryArg[0] + "\n");

                    // receive the message back from server
                    // close the starting thread UDP connection
                    // condition is false, the client end the process
                    String reply = bfServe.readLine();
                    System.out.println(reply);
                    c.close();
                    condition = false;
                }

            } else if (queryArg[0].compareTo("UPD") == 0) {
                // check other 'UPD' command argument is valid 
                if (!checkUdp(queryArg)) {
                    System.out.println(errorMessage);
                } else {
                    // send the 'UPD' and desired client name to server
                    outToServer.writeBytes(queryArg[0] + "\n");
                    outToServer.writeBytes(queryArg[1] + "\n");

                    // receive the message back from server
                    // if the message is "user is not active" -> print it out
                    // else, send the packet to the desired user
                    String reply = bfServe.readLine();
                    if (reply.compareTo("user is not active") == 0) {
                        System.out.println(reply);
                    } else {
                        InetAddress desiredAddress = InetAddress.getByName(reply);
                        String desiredUdpPort = bfServe.readLine();
                        String myName = bfServe.readLine();

                        // send the file by opening the 'DatagramSocket'
                        DatagramSocket clientS = new DatagramSocket();

                        // send the filename and sender name to the desired client server
                        byte[] firstSendData=new byte[1024];
                        firstSendData = queryArg[2].getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(firstSendData, firstSendData.length, desiredAddress, Integer.parseInt(desiredUdpPort));
                        clientS.send(sendPacket);
                    
                        byte[] secondSendData=new byte[1024];
                        secondSendData = myName.getBytes();
                        DatagramPacket newPacket = new DatagramPacket(secondSendData, secondSendData.length, desiredAddress, Integer.parseInt(desiredUdpPort));
                        clientS.send(newPacket);


                        // send the content of file to the desired client server, send the confirmation 'done' after finishing
                        byte b[] = new byte[1024];
                        FileInputStream f = new FileInputStream(queryArg[2]);
                        int readLength;
                        while (f.available() != 0) {
                            readLength = f.read(b);
                            clientS.send(new DatagramPacket(b, readLength, desiredAddress, Integer.parseInt(desiredUdpPort)));
                        }

                        String confirm = "done";
                        byte[] lastSendData =new byte[1024];
                        lastSendData = confirm.getBytes();
                        sendPacket = new DatagramPacket(lastSendData, lastSendData.length, desiredAddress, Integer.parseInt(desiredUdpPort));
                        clientS.send(sendPacket);

                        System.out.println(queryArg[2] + " has been uploaded");

                        f.close();
                        clientS.close();
                    }
                }
            } else {
                System.out.println(errorMessage);
            }
        }

    }

    // udp argument can only be 3
    public static boolean checkUdp(String[] queryArg) {
        if (queryArg.length != 3) {
            return false;
        }
        return true;
    }

    // out argument can only be 1
    public static boolean checkOut(String[] queryArg) {
        if (queryArg.length != 1) {
            return false;
        }
        return true;
    }

    // atu argument can only be 1
    public static boolean checkAtu(String[] queryArg) {
        if (queryArg.length != 1) {
            return false;
        }
        return true;
    }

    // msg argument can only be 2
    public static boolean checkMsg(String[] queryArg) {
        if (queryArg.length == 1) {
            return false;
        }
        return true;
    }
    
    // dlt argument can only be 6
    public static boolean checkDlt(String[] queryArg) {
        if (queryArg.length != 6) {
            return false;
        } 
        // the message number format is not right
        if (queryArg[1].substring(0, 1).compareTo("#") != 0) {
            return false;
        } 
        // the message number format has "#" but not has number
        if (queryArg[1].substring(0, 1).compareTo("#") == 0) {
            try {
                int messNum = Integer.parseInt(queryArg[1].substring(1));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        // check the time format is right
        String timeFormat = queryArg[2] + " " + queryArg[3] + " " + queryArg[4] + " " + queryArg[5];
        DateTimeFormatter strictTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
        try {
            LocalTime.parse(timeFormat, strictTimeFormatter);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    // dlt argument can only be 7 or more
    public static boolean checkEdt(String[] queryArg) {
        if (queryArg.length < 7 ) {
            return false;
        }
        // the message number format is not right
        if (queryArg[1].substring(0, 1).compareTo("#") != 0) {
            return false;
        } 
        // the message number format has "#" but not has number
        if (queryArg[1].substring(0, 1).compareTo("#") == 0) {
            try {
                int messNum = Integer.parseInt(queryArg[1].substring(1));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // check the time format is right
        String timeFormat = queryArg[2] + " " + queryArg[3] + " " + queryArg[4] + " " + queryArg[5];
        DateTimeFormatter strictTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
        try {
            LocalTime.parse(timeFormat, strictTimeFormatter);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    // RDM argument can only be 5
    public static boolean checkRdm(String[] queryArg) {
        if (queryArg.length != 5) {
            return false;
        }

        // check the time format is right
        String timeFormat = queryArg[1] + " " + queryArg[2] + " " + queryArg[3] + " " + queryArg[4];
        DateTimeFormatter strictTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
        try {
            LocalTime.parse(timeFormat, strictTimeFormatter);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }


    public static boolean clientAuthentication(BufferedReader bfserver, DataOutputStream outtoserver) throws Exception{
        // client type in the user info
        BufferedReader bfInput = new BufferedReader(new InputStreamReader(System.in));
        String messageToServer = "";
        System.out.print("Username: ");
        messageToServer += bfInput.readLine();
        messageToServer += " ";
        System.out.print("Password: ");
        messageToServer += bfInput.readLine();
        messageToServer += "\n";

        // client sent the user info to server
        // assumption, the user name will be the name inside the credential.txt file
        outtoserver.writeBytes(messageToServer);
        
        // read the response from the server
        // 'welcome to TOOM' -> return true, pass the authentication
        // 'Invalid password, please try again' -> type the password and send to server
        // other cases -> print the error message sent back from server, return false, fail the authentication
        while (true)
        {
            String messageFromServer = bfserver.readLine();
            if (messageFromServer.compareTo("Welcome to TOOM!") == 0) 
            {
                return true;
            } else if (messageFromServer.compareTo("Your account is blocked due to multiple login failures, Please try again later") == 0)
            {
                System.out.println("Your account is blocked due to multiple login failures, Please try again later");
                return false;
            } else if (messageFromServer.compareTo("Invalid Password. Your account has been blocked. Please try again later") == 0) 
            {
                System.out.println("Invalid Password. Your account has been blocked. Please try again later");
                return false;
            } else if (messageFromServer.compareTo("Invalid Password. Please try again") == 0) 
            {
                System.out.println("Invalid Password. Please try again");
                System.out.print("Password: ");
                messageToServer = bfInput.readLine();
                messageToServer += "\n";
                outtoserver.writeBytes(messageToServer);
            }
        }
    }
}
