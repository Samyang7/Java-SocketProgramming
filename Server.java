import java.io.*;
import java.net.*;
import java.nio.file.NotLinkException;
import java.security.MessageDigest;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.DataFormatException;

import javax.management.Query;
import javax.print.DocFlavor.STRING;


// Multiclient handler class
class MultiClientHandler extends Thread 
{
    Socket clientConnetS;
    BufferedReader fromClient;
    DataOutputStream toClient;
    HashMap<String, Timestamp> invalidUser;
    int limitAttempt;
    String user;
    HashMap<String, String[]> activeUser;

    // constant: block period
    public final static int DURATION = 10000;

    // constructor for storing the necessary client information inside the 'Multiclient handler' object 
    public MultiClientHandler(Socket s, BufferedReader fromclient, DataOutputStream toclient, HashMap<String, Timestamp> invaliduser, int limitattempt, HashMap<String, String[]> activeUser) 
    {
        this.clientConnetS = s;
        this.fromClient = fromclient;
        this.toClient = toclient;
        this.invalidUser = invaliduser;
        this.limitAttempt = limitattempt;
        this.user = "";
        this.activeUser = activeUser;

    }

    // function for checking the user information
    // client pass the authentication -> return true
    // client fail the authentication -> return false
    public boolean serverAuthentication() throws Exception
    {
        
        // if there is nothing typed from client -> return false
        String messagefromClient = this.fromClient.readLine();
        if (messagefromClient == null || messagefromClient.length() == 0) {
            return false;
        }
        String[] userInfo = messagefromClient.split(" ");
        String messageToClient;

        // check invalidUser record in the HashMap
        // 'currenttime - recordtime > block period' -> remove the invalidUser record in the HashMap
        // 'currenttime - recordtime > block period' -> print the block message, return false
        if (this.invalidUser.containsKey(userInfo[0])) 
        {
            Timestamp currentTimeStamp = new Timestamp(System.currentTimeMillis());
            long diff = (currentTimeStamp.getTime() - (this.invalidUser.get(userInfo[0])).getTime());
            if (diff >= DURATION) 
            {
                this.invalidUser.remove(userInfo[0]);
            } else 
            {
                messageToClient = "Your account is blocked due to multiple login failures, Please try again later\n";
                this.toClient.writeBytes(messageToClient);
                return false;
            }
        }

        // assumption: the typed in username is in the credential.txt file, the password maybe wrong
        // check the 'credentials file' to find the user info record
        // if the user typed the wrong one -> store the expected password inside 'validPassword' for later checking
        BufferedReader credfile = new BufferedReader(new FileReader("credentials.txt"));
        String record;
        String validPassword = null;
        int flag = 0;
        while ((record = credfile.readLine()) != null) 
        {
            String[] recordList = record.split(" ");
            if (record.compareTo(messagefromClient) == 0) 
            {
                flag = 1;
            } else 
            {
                if ((recordList[0].compareTo(userInfo[0]) == 0) && (recordList[1].compareTo(userInfo[1])) != 0) 
                {
                    validPassword = recordList[1];
                }
            }
        }
        credfile.close();

        // valid user(flag == 1) -> send the welcome message, return true
        // invalid password(flag == 0) -> print invalid message, keep checking before reach the try limination
        if (flag == 1) 
        {
            messageToClient = "Welcome to TOOM!\n";
            this.user = userInfo[0];
            this.toClient.writeBytes(messageToClient);
            return true;
        } else 
        {
            messageToClient = "Invalid Password. Please try again\n";
            this.toClient.writeBytes(messageToClient);

            int tryTime = 0;
            while (tryTime < this.limitAttempt-1) 
            {
                messagefromClient = this.fromClient.readLine();
                tryTime += 1;
                // in case the user entered ctrl + c, put validPassword != null 
                if (validPassword != null && messagefromClient.compareTo(validPassword) == 0)
                {
                    messageToClient = "Welcome to TOOM!\n";
                    this.user = userInfo[0];
                    this.toClient.writeBytes(messageToClient);
                    return true;
                } else 
                {
                    if (tryTime == this.limitAttempt-1)
                    {
                        break;
                    }
                    messageToClient = "Invalid Password. Please try again\n";
                    this.toClient.writeBytes(messageToClient);
                }

            }

            // reach to this point -> user tried many times that pass the limination -> block user, put the user into the invalidUser record, return false
            invalidUser.put(userInfo[0], new Timestamp(System.currentTimeMillis()));
            messageToClient = "Invalid Password. Your account has been blocked. Please try again later\n";
            this.toClient.writeBytes(messageToClient);
            return false;
        }
 
    }

    // user pass the authentication -> write their login record into 'userlog.txt' file
    public synchronized void writeRecord(String udpNumber) throws Exception
    {
        SimpleDateFormat timeformat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
        String currentTime = timeformat.format(new Date());
        String userInformation = currentTime + "; " + this.user + "; " + (this.clientConnetS.getInetAddress()).getHostAddress() + "; " + udpNumber;

        BufferedReader logRecord = new BufferedReader(new FileReader("userlog.txt"));
        BufferedWriter myWriter = new BufferedWriter(new FileWriter("userlog.txt", true));
        File f = new File("userlog.txt");
        
        // file is empty -> write the record as the first user
        // file not empty -> extract the last line to get the latest user number, increment by 1
        if (f.length() == 0) {
            String record = "1; " + userInformation;
            //System.out.println(record);
            myWriter.append(record + '\n');
        } else {
            
            String lastLine = ""; String line = "";
            while ((line = logRecord.readLine()) != null) {
                lastLine = line;
            }
            int userNum = Integer.parseInt(lastLine.split(";")[0]) + 1;
            String record = Integer.toString(userNum) + "; " + userInformation;
            //System.out.println(record);
            myWriter.append(record + '\n');
        }

        // add the user information to the 'activeUser' -> server for later client p2p command(UPD)
        String[] info = new String[3];
        info[0] = currentTime;
        info[1] = this.clientConnetS.getInetAddress().getHostAddress();
        info[2] = udpNumber;
        this.activeUser.put(this.user, info);

        logRecord.close();
        myWriter.close();
    }

    // Server function for responding "MSG" command
    // client already took care of the argument format checking 
    public synchronized boolean msgInteract(String message) throws Exception{
        BufferedReader messageRecord = new BufferedReader(new FileReader("messagelog.txt"));
        BufferedWriter myWriter = new BufferedWriter(new FileWriter("messagelog.txt", true));
        File f = new File("messagelog.txt");

        // write down the message log to 'messagelog.txt'
        // file is empty -> message number is 1
        // file not empty -> get the latest message number + 1
        SimpleDateFormat timeformat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
        String currentTime = timeformat.format(new Date());
        int userNum;
        if (f.length() == 0) {
            userNum = 1;
        } else {
            String lastLine = ""; String line = "";
            while ((line = messageRecord.readLine()) != null) {
                lastLine = line;
            }
            userNum = Integer.parseInt(lastLine.split(";")[0]) + 1;
        }
        // write to the file
        String messageLog = Integer.toString(userNum) + "; " + currentTime + "; " + this.user + "; " + message + "; "+ "no"; 
        String response = this.user + " posted MSG #" + Integer.toString(userNum) + " \"" + message + "\" at " + currentTime + ".";
        System.out.println(response);
        myWriter.append(messageLog + '\n');

        // Server send reponse back to client: timestamp, userNum
        this.toClient.writeBytes(Integer.toString(userNum) + "\n");
        this.toClient.writeBytes(currentTime + "\n");
        messageRecord.close();
        myWriter.close();

        return true;
    }

    // Server function for responding "DLT" command 
    // client already took care of the argument format checking 
    public synchronized boolean dltInteract(String messageNum, String ts) throws Exception {
        BufferedReader messageRecord = new BufferedReader(new FileReader("messagelog.txt"));
        BufferedWriter myWriter = new BufferedWriter(new FileWriter("messagelog.txt", true));
        File f = new File("messagelog.txt");

        // check the messageNum is valid
        // the file is empty or the number of line is smaller than the argument -> return true to exit
        String errorMessage = "deletion operation is not valid, the message Number is not right";
        if (f.length() == 0) {
            this.toClient.writeBytes(errorMessage+"\n");
            myWriter.close();
            messageRecord.close();
            return true;
        } else {
            int totaLine = 0; String line = "";
            while ((line = messageRecord.readLine()) != null) {
                totaLine += 1;
            }
            if (totaLine < Integer.parseInt(messageNum)) {
                this.toClient.writeBytes(errorMessage+"\n");
                myWriter.close();
                messageRecord.close();
                return true;
            }
            messageRecord.close();
        }

        // pass the messageNumber check, then check the time and user is correct 
        BufferedReader messageR = new BufferedReader(new FileReader(f));
        String line = "";
        while ((line = messageR.readLine()) != null) {
            if (line.split(";")[0].compareTo(messageNum) == 0) {
                break;
            }
        }
        messageR.close();
        if ((line.split("; ")[1]).compareTo(ts) != 0) {
            errorMessage = "deletion operation is not valid, the time stamp is not right";
            this.toClient.writeBytes(errorMessage+"\n");
            myWriter.close();
            return true;
        } else if (line.split("; ")[2].compareTo(this.user) != 0) {
            errorMessage = "deletion operation is not valid, the user is not right";
            this.toClient.writeBytes(errorMessage+"\n");
            myWriter.close();
            return true;
        } 

        // pass the messageNumber check and time and user check 
        // create a tmp file read the line from old file to tmp file but escape the line
        // need to decrement the line number of line below the line that we want to delete
        File tempFile= new File("tmpmessagelog.txt");
        BufferedReader inputFileReader = new BufferedReader(new FileReader(f));
        BufferedWriter outputFileWriter = new BufferedWriter(new FileWriter(tempFile));
        String message = null; 
        String currentLine;
        int mark = 0;
        while ((currentLine = inputFileReader.readLine()) != null) {
            String lineNum = currentLine.split(";")[0];
            if (lineNum.compareTo(messageNum) == 0) {
                message = currentLine.split("; ")[3];
                mark = 1;
                continue;
            }
            if (mark == 1) {
                String newlineNum = Integer.toString(Integer.parseInt(lineNum) - 1);
                String newLine = newlineNum + "; " + currentLine.substring(currentLine.split(";")[0].length()+2);
                outputFileWriter.write(newLine + "\n");
            } else {
                outputFileWriter.write(currentLine + "\n");
            }
        }

        // close the stream and delete the original file and rename the tmp filename to original file name
        outputFileWriter.close();
        inputFileReader.close();
        f.delete();
        tempFile.renameTo(f);
        String response = this.user + " deleted MSG #" + messageNum + " \"" + message + "\" at " + ts + ".";
        System.out.println(response);

        // Server send reponse back to client: delete message
        this.toClient.writeBytes("Message #" + messageNum + " delete at " + ts + ".\n");
        myWriter.close();

        return true;
    }

    // Server function for responding "EDT" command 
    // client already took care of the argument format checking 
    public synchronized boolean edtInteract (String messageNum, String ts, String newMessage) throws Exception {
        BufferedReader messageRecord = new BufferedReader(new FileReader("messagelog.txt"));
        BufferedWriter myWriter = new BufferedWriter(new FileWriter("messagelog.txt", true));
        File f = new File("messagelog.txt");

        // check the messageNum is valid
        // the file is empty or the number of line is smaller than the argument -> return true to exit
        String errorMessage = "edit operation is not valid, the message Number is not right";
        if (f.length() == 0) {
            this.toClient.writeBytes(errorMessage+"\n");    
            messageRecord.close();
            myWriter.close();
            return true;
        } else {
            int totaLine = 0; String line = "";
            while ((line = messageRecord.readLine()) != null) {
                totaLine += 1;
            }
            if (totaLine < Integer.parseInt(messageNum)) {
                this.toClient.writeBytes(errorMessage+"\n");
                messageRecord.close();
                myWriter.close();;
                return true;
            }
            messageRecord.close();
        }

        // pass the messageNumber check, then check the time and user is correct 
        BufferedReader messageR = new BufferedReader(new FileReader(f));
        String line = "";
        while ((line = messageR.readLine()) != null) {
            if (line.split(";")[0].compareTo(messageNum) == 0) {
                break;
            }
        }
        messageR.close();
        if ((line.split("; ")[1]).compareTo(ts) != 0) {
            errorMessage = "edited operation is not valid, the time stamp is not right";
            this.toClient.writeBytes(errorMessage+"\n");
            myWriter.close();
            return true;
        } else if (line.split("; ")[2].compareTo(this.user) != 0) {
            errorMessage = "Unauthorised to edit Message #" + line.split(";")[0] + ".";
            this.toClient.writeBytes(errorMessage+"\n");
            myWriter.close();
            return true;
        }

        // pass the messageNumber check and time and user check 
        // create a tmp file read the line from old file to tmp file but modify the line
        // the message number keeps the same, need to change 'no' to 'yes'
        File tempFile= new File("tmpmessagelog.txt");
        BufferedReader inputFileReader = new BufferedReader(new FileReader(f));
        BufferedWriter outputFileWriter = new BufferedWriter(new FileWriter(tempFile));
        String currentLine = null;
        String currentTime = null;
        while ((currentLine = inputFileReader.readLine()) != null) {
            String lineNum = currentLine.split(";")[0];
            if (lineNum.compareTo(messageNum) == 0) {
                SimpleDateFormat timeformat = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
                currentTime = timeformat.format(new Date());
                String newLine = messageNum + "; " + currentTime + "; " + this.user + "; " + newMessage + "; " + "yes";
                outputFileWriter.write(newLine + "\n");
            } else {
                outputFileWriter.write(currentLine + "\n");
            }
        }

        // close the stream and delete the original file and rename the tmp filename to original file name
        outputFileWriter.close();
        inputFileReader.close();
        f.delete();
        tempFile.renameTo(f);
        String respone = this.user + " edited MSG #" + messageNum + " \"" + newMessage + "\" at " + currentTime + ".";
        System.out.println(respone);

        // Server send reponse back to client: edit message
        this.toClient.writeBytes("Message " + messageNum + " edited at " + currentTime + ".\n");
        myWriter.close();
        return true;
    }

    // Server function for responding "RDM" command 
    // client already took care of the argument format checking 
    public synchronized boolean rdmInteract (String ts) throws Exception {
        BufferedReader messageRecord = new BufferedReader(new FileReader("messagelog.txt"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");
        int totalMatch = 0;
        String currenLine = "";
        ArrayList<String> matchline = new ArrayList<>();
        ArrayList<String> responseLine = new ArrayList<>();
        Date d1 = sdf.parse(ts);

        // get the message timestamp and compared with the asking timestamp
        // store the line record into 'matchline' and 'responseline' arraylist
        // 'matchline' is for printing the message in the server side
        // 'responseline' is for sending the message back to client side
        while ((currenLine = messageRecord.readLine()) != null) {
            String[] lineList = currenLine.split("; ");
            String time = lineList[1];
            Date d2 = sdf.parse(time);
            long timeDiff = d2.getTime() - d1.getTime();
            if (timeDiff > 0) {
                String condition;
                if (lineList[4].compareTo("yes") == 0) {
                    condition = "edited at ";
                } else {
                    condition = "posted at ";
                }
                String s = "#"+lineList[0] + " " + lineList[2] + ", " + lineList[3] + ", " + condition + lineList[1] + "."; 
                String s2 = "#"+lineList[0]+ ", " + lineList[2] + ": " + lineList[3] + ", " + condition + lineList[1] + ".";
                matchline.add(s);
                responseLine.add(s2);
                totalMatch += 1;
            }
        }

        // no match case -> send 0 and 'no new message'
        // match cases -> send totalmatch times and responseline, print matchline
        System.out.println("Return messages:");
        if (totalMatch == 0) {
            this.toClient.writeBytes("0" + "\n");
            this.toClient.writeBytes("no new message" + "\n");
        } else {
            this.toClient.writeBytes(Integer.toString(totalMatch) + "\n");
            for (int i = 0; i < totalMatch; i++) {
                this.toClient.writeBytes(responseLine.get(i) + "\n");
                System.out.println(matchline.get(i));
            }
        }
        messageRecord.close();
        return true;
    }  

    // Server function for responding "ATU" command
    // client already took care of the argument format checking 
    public synchronized boolean atuInteract() throws Exception {
        
        System.out.println(this.user + " issued ATU command ATU.");
        // check the 'activeUser' hashmap
        // activerUser.size = 1 -> only client itself, no other active user
        // activerUser.size > 1 -> loop through and print it out
        if (this.activeUser.size() == 1) {
            System.out.println("no other active user");
            this.toClient.writeBytes("0\n" );
            this.toClient.writeBytes("no other active user \n");
        } else {
            System.out.println("Return active user list: ");
            this.toClient.writeBytes(Integer.toString(this.activeUser.size()-1) + "\n");
            for (String username: this.activeUser.keySet()) {
                if (username.compareTo(this.user) != 0) {
                    String[] userInfo = this.activeUser.get(username);
                    String s = username + ", " + userInfo[1] + ", " + userInfo[2] + ", active since " + userInfo[0] + ".";
                    System.out.println(s);
                    this.toClient.writeBytes(s + "\n");
                }
            }
        }

        return true;
    }

    // Server function for responding "OUT" command
    // client already took care of the argument format checking 
    public synchronized boolean outInteract() throws Exception {
        BufferedReader logRecord = new BufferedReader(new FileReader("userlog.txt"));
        File f = new File("userlog.txt");

        // create a tmp file read the line from old file to tmp file but skip the line
        // need to decrement the line number of line below the line that we want to delete
        File tempFile= new File("tmpuserlog.txt");
        BufferedReader inputFileReader = new BufferedReader(new FileReader(f));
        BufferedWriter outputFileWriter = new BufferedWriter(new FileWriter(tempFile));
        String message = null; 
        String currentLine;
        int mark = 0;
        while ((currentLine = inputFileReader.readLine()) != null) {
            String lineNum = currentLine.split("; ")[2];
            if (lineNum.compareTo(this.user) == 0) {
                mark = 1;
                continue;
            }
            if (mark == 1) {
                String newlineNum = Integer.toString(Integer.parseInt(currentLine.split("; ")[0]) - 1);
                String newLine = newlineNum + "; " + currentLine.substring(currentLine.split(";")[0].length()+2);
                outputFileWriter.write(newLine + "\n");
            } else {
                outputFileWriter.write(currentLine + "\n");
            }
        }
        // close the stream, delete the file and rename the new filename to the old one
        outputFileWriter.close();
        inputFileReader.close();
        f.delete();
        tempFile.renameTo(f);

        // remove the user from the activeUser hashmap
        // Server send reponse back to client: bye message
        this.activeUser.remove(this.user);
        System.out.println(this.user + " logout");
        this.toClient.writeBytes("Bye, " + this.user + "!\n");
        
        return false;
    }

    // Server function for responding "UPD" command
    // client already took care of the argument format checking 
    public synchronized boolean udpInteract(String username) throws Exception {
        // check if the username is active at the moment
        // not active -> send the error message
        // active -> send the address and portnumber, username
        if (!this.activeUser.containsKey(username)) {
            this.toClient.writeBytes("user is not active\n");
        } else {
            this.toClient.writeBytes(this.activeUser.get(username)[1] + "\n");
            this.toClient.writeBytes(this.activeUser.get(username)[2] + "\n");
            this.toClient.writeBytes(this.user + "\n");
        }
        return true;
    }
        
    public void run() 
    {
        // authentication
        // if pass the authentication, server will write down the log record into "userlog.txt"
        try {
            if (this.serverAuthentication()) 
            {
                // server get the message from the client, udpportnumber of client, write down the login info into 'userlog.txt'
                String udpNum = this.fromClient.readLine();
                this.writeRecord(udpNum);

                // server then wait for uncoming message from the client, the server needs to be on all the time
                // each response function will return either true or false
                // turn -> keep the server and client thread (MSG, DLT, EDT, RDM, UPD, ATU)
                // false -> server and client thread end (OUT)
                boolean texting = true;
                while (texting) {
                    // read the query, query includes ('MSG', 'DLT', 'EDT', 'RDM', 'OUT', 'UPD', 'ATU')
                    String queryFromClient = this.fromClient.readLine();
                    if (queryFromClient != null) {
                        // query: MSG; clientMessage: message
                        if (queryFromClient.compareTo("MSG") == 0) {
                            String message = this.fromClient.readLine();
                            texting = this.msgInteract(message);

                        // query: DLT; clientMessage: timestamp
                        } else if (queryFromClient.compareTo("DLT") == 0) {
                            String messageNum = this.fromClient.readLine();
                            String ts = this.fromClient.readLine();
                            texting = this.dltInteract(messageNum, ts);

                        // query: EDT; clientMessage: messageNum, timestamp, newmessage
                        } else if (queryFromClient.compareTo("EDT") == 0) {
                            String messageNum = this.fromClient.readLine();
                            String ts = this.fromClient.readLine();
                            String newMessage = this.fromClient.readLine();
                            texting = this.edtInteract(messageNum, ts, newMessage);
                        
                        // query: "RDM"; clientMessage: timestamp
                        } else if (queryFromClient.compareTo("RDM") == 0) {
                            String ts = this.fromClient.readLine();
                            System.out.println(this.user + " issued RDM command");
                            texting = this.rdmInteract(ts);
                        
                        // query: "OUT"; no clientMessage
                        } else if (queryFromClient.compareTo("OUT") == 0) {
                            texting = this.outInteract();

                        // query: "OUT"; no clientMessage
                        } else if (queryFromClient.compareTo("ATU") == 0) {
                            texting = this.atuInteract();
                        
                        // query: "OUT"; no clientMessage
                        } else if (queryFromClient.compareTo("UPD") == 0) {
                            String username = this.fromClient.readLine();
                            texting = this.udpInteract(username);
                        }
                    }
                }
                
            } 
        } catch (Exception e) {
            System.out.println(e);
            System.out.println("authentication error");
        }

    }

}


public class Server 
{    
    public static void main(String[] args) throws Exception 
    {
        // check the number of argument
        if (args.length < 2) 
        {
            System.out.println("Usage: java Server server_port number_of_consecutive_failed_attempts");
            System.exit(1);
        }
        int TcpServerPort = Integer.parseInt(args[0]);
        int numberOfAttempts = Integer.parseInt(args[1]);
        // check the number of consecutive try is valid
        if (numberOfAttempts > 10 || numberOfAttempts == 0) 
        {
            System.out.println("Invalid number of allowed failed consecutive attempt: " + Integer.toString(numberOfAttempts));
        }

        // create the Welcome serversocket
        // 'invalidUser': hashmap used for record which user is blocked
        // 'activeClient': hashmap used for record which user client is also online except itself
        ServerSocket serverSocket = new ServerSocket(TcpServerPort);
        HashMap<String, Timestamp> invalidUser = new HashMap<>();
        DataOutputStream toClient = null;
        BufferedReader  fromClient = null;
        HashMap<String, String[]> activeClient = new HashMap<>();
        
        // server stays in the loop and listens to the client
        while (true) 
        {
            Socket connectSocket = serverSocket.accept();
            fromClient = new BufferedReader(new InputStreamReader(connectSocket.getInputStream()));
            toClient = new DataOutputStream(connectSocket.getOutputStream());

            // start a new seperated thread when there is a new client connection
            // put the 'invalidUser' 'activeClient' 'number of attempts' 'toclient'Stream, and 'fromClient'Steam into the object
            // t.start() will call the run function inside the Thread t object
            Thread t = new MultiClientHandler(connectSocket, fromClient, toClient, invalidUser, numberOfAttempts, activeClient);
            t.start();

        }
        
    }


}
